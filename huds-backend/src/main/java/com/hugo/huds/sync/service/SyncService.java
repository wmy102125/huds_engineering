package com.hugo.huds.sync.service;

import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.sync.dispatcher.SyncDispatcher;
import com.hugo.huds.sync.dispatcher.SyncEntityType;
import com.hugo.huds.sync.dto.*;
import com.hugo.huds.sync.entity.SyncMutation;
import com.hugo.huds.sync.repository.SyncMutationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.hugo.huds.sync.dto.SyncPushResultResponse.mutationIdReused;

/**
 * Processes a push batch: validates the envelope, then handles the mutations one by one.
 * <p>
 * This class is deliberately NOT {@code @Transactional}: every mutation runs in its own transaction.
 *
 * @author Meiyan Wang
 * @date 2026-10-20 10:46 a.m.
 */
@Slf4j
@Service
public class SyncService {
    //private static final int MAX_PULL_LIMIT = 2;
    private static final int MAX_PULL_LIMIT = 500;
    private static final UUID MIN_ID = new UUID(0L, 0L);

    private static final int MAX_MUTATIONS = 100;

    private final SyncMutationRepository syncMutationRepository;
    private final SyncDispatcher syncDispatcher;
    private final SyncDependencyChecker syncDependencyChecker;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;
    private final TransactionTemplate newTx;
    private final SyncPermissionChecker syncPermissionChecker;

    public SyncService(SyncMutationRepository syncMutationRepository, SyncDispatcher syncDispatcher,
                       SyncDependencyChecker syncDependencyChecker, ObjectMapper objectMapper,
                       PlatformTransactionManager txManager, SyncPermissionChecker syncPermissionChecker) {
        this.syncMutationRepository = syncMutationRepository;
        this.syncDispatcher = syncDispatcher;
        this.syncDependencyChecker = syncDependencyChecker;
        this.objectMapper = objectMapper;
        this.tx = new TransactionTemplate(txManager);
        // Used to record REJECTED outcomes after the business transaction has rolled back.
        this.newTx = new TransactionTemplate(txManager);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.syncPermissionChecker = syncPermissionChecker;
    }

    /**
     * Downloads one batch of changes of an entity type.
     *
     * @param entityType the business type
     * @param since      the cursor time, or null for the first download
     * @param sinceId    the cursor id, or null for the first download
     * @param limit      the maximum number of rows, null for the default
     * @return the batch and the cursor to continue with
     */
    public SyncPullResponse pull(SyncEntityType entityType, Instant since, UUID sinceId, Integer limit) {
        if (!syncDispatcher.supports(entityType)) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD,
                    "Unsupported entityType " + entityType + ".");
        }
        if ((since == null) != (sinceId == null)) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD,
                    "since and sinceId must be sent together.");
        }
        int size = limit == null ? MAX_PULL_LIMIT : Math.clamp(limit, 1, MAX_PULL_LIMIT);
        // No cursor means the first download: start from the very beginning.
        Instant start = since == null ? Instant.EPOCH : since;
        UUID startId = sinceId == null ? MIN_ID : sinceId;
        return syncDispatcher.pullDispatch(entityType, start, startId, size);
    }


    /**
     * Processes a push batch in order, one mutation at a time.
     *
     * @param caller  the current caller
     * @param request the batch to process
     * @return the result of every mutation, in the same order
     */
    public SyncPushResponse push(SyncCaller caller, SyncPushRequest request) {
        UUID actorUserId = caller.userId();
        validateEnvelope(actorUserId, request);

        List<SyncPushResultResponse> results = new ArrayList<>();
        // The outcome of every mutation processed so far, used by later mutations that depend on them.
        Map<UUID, SyncPushStatus> batchResults = new HashMap<>();

        for (SyncPushMutationRequest mutation : request.mutations()) {
            SyncPushResultResponse result = processOne(caller, mutation, batchResults);
            batchResults.put(mutation.mutationId(), result.status());
            results.add(result);
        }
        return new SyncPushResponse(Instant.now(), results);
    }

    /**
     * Processes one mutation: idempotency, dependencies, then the business change in its own transaction.
     */
    private SyncPushResultResponse processOne(SyncCaller caller, SyncPushMutationRequest mutation,
                                              Map<UUID, SyncPushStatus> batchResults) {
        UUID actorUserId = caller.userId();
        // 1. Idempotency: was this mutation already processed for this user?
        Optional<SyncMutation> existing = syncMutationRepository
                .findByActorUserIdAndClientMutationId(actorUserId, mutation.mutationId());
        if (existing.isPresent()) {
            return replay(existing.get(), mutation);
        }

        // 2. Dependencies: a failed dependency blocks this mutation. Nothing is executed or stored.
        List<UUID> blockedBy = syncDependencyChecker.blockingDependencies(
                actorUserId, mutation.dependsOn(), batchResults);
        if (!blockedBy.isEmpty()) {
            return SyncPushResultResponse.blocked(mutation, blockedBy);
        }

        try {
            // A caller without the permission gets a recorded REJECTED outcome, like any other business rejection.
            syncPermissionChecker.check(caller, mutation.entityType());

            // 3. The business change and the ACCEPTED record commit together, or not at all.
            return tx.execute(status -> {
                Long version = syncDispatcher.pushDispatch(actorUserId, mutation);
                SyncPushResultResponse result = SyncPushResultResponse.accepted(mutation, version);
                saveOutcome(caller, mutation, result);
                return result;
            });
        } catch (ApplicationException ex) {
            log.info("Mutation {} ({} {}) rejected: {}",
                    mutation.mutationId(), mutation.entityType(), mutation.operation(), ex.getMessage());
            // 4. The transaction above has rolled back. Record the rejection in a new transaction.
            return newTx.execute(status -> {
                SyncPushResultResponse rejected = SyncPushResultResponse.rejected(mutation, ex);
                saveOutcome(caller, mutation, rejected);
                return rejected;
            });
        }
        // Any other exception (database down, bug) propagates: nothing is recorded and the client retries.
    }

    /**
     * Validates the whole batch before anything is processed. Any violation rejects the whole request.
     */
    private void validateEnvelope(UUID actorUserId, SyncPushRequest request) {
        // The outbox must belong to the user who is logged in.
        if (!actorUserId.equals(request.outboxOwnerUserId())) {
            throw new ApplicationException(ErrorCode.OUTBOX_OWNER_MISMATCH,
                    "The outbox belongs to a different user.");
        }

        List<SyncPushMutationRequest> mutations = request.mutations();
        if (mutations == null || mutations.isEmpty() || mutations.size() > MAX_MUTATIONS) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD,
                    "A batch must contain between 1 and " + MAX_MUTATIONS + " mutations.");
        }

        Set<UUID> mutationIdsSoFar = new HashSet<>();
        Set<UUID> entityIds = new HashSet<>();
        // Dependencies that are not earlier in this batch: they must have been processed before.
        Set<UUID> outsideDependencies = new HashSet<>();

        for (SyncPushMutationRequest mutation : mutations) {
            UUID mutationId = mutation.mutationId();
            if (mutationIdsSoFar.contains(mutationId)) {
                throw new ApplicationException(ErrorCode.INVALID_PAYLOAD,
                        "The mutation id " + mutationId + " is duplicated.");
            }
            if (!syncDispatcher.supports(mutation.entityType())) {
                throw new ApplicationException(ErrorCode.INVALID_PAYLOAD,
                        "Unsupported entityType " + mutation.entityType() + ", mutation id is " + mutationId);
            }
            // One operation per entity in a batch: the client has to merge them before sending.
            if (!entityIds.add(mutation.entityId())) {
                throw new ApplicationException(ErrorCode.INVALID_PAYLOAD,
                        "More than one mutation targets entity " + mutation.entityId() + ".");
            }

            Set<UUID> declared = new HashSet<>();
            for (UUID dependencyId : mutation.dependsOn()) {
                if (dependencyId.equals(mutationId)) {
                    throw new ApplicationException(ErrorCode.INVALID_PAYLOAD,
                            "The mutation " + mutationId + " depends on itself.");
                }
                if (!declared.add(dependencyId)) {
                    throw new ApplicationException(ErrorCode.INVALID_PAYLOAD,
                            "The mutation " + mutationId + " lists the dependency " + dependencyId + " twice.");
                }
                if (!mutationIdsSoFar.contains(dependencyId)) {
                    outsideDependencies.add(dependencyId);
                }
            }
            // Only mutations that come earlier can be depended on, so a dependency cycle cannot exist.
            mutationIdsSoFar.add(mutationId);
        }

        if (!outsideDependencies.isEmpty()) {
            Set<UUID> processedBefore = syncMutationRepository
                    .findByActorUserIdAndClientMutationIdIn(actorUserId, outsideDependencies).stream()
                    .map(SyncMutation::getClientMutationId)
                    .collect(Collectors.toSet());
            outsideDependencies.removeAll(processedBefore);
            if (!outsideDependencies.isEmpty()) {
                throw new ApplicationException(ErrorCode.INVALID_PAYLOAD,
                        "Unknown dependencies: " + outsideDependencies
                                + ". A dependency must come earlier in the batch or have been processed before.");
            }
        }
    }

    /**
     * Returns the stored outcome of a mutation that was processed before.
     */
    private SyncPushResultResponse replay(SyncMutation existing, SyncPushMutationRequest request) {
        boolean sameMutation = Objects.equals(existing.getEntityType(), request.entityType().name())
                && existing.getEntityId().equals(request.entityId())
                && Objects.equals(existing.getOperation(), request.operation().name());
        // The same mutation id was used for a different mutation.
        if (!sameMutation) {
            return mutationIdReused(request);
        }

        SyncPushResultResponse stored = objectMapper.readValue(existing.getResponsePayload(), SyncPushResultResponse.class);
        return stored.asReplay();
    }

    private void saveOutcome(SyncCaller caller, SyncPushMutationRequest request, SyncPushResultResponse resultResponse) {
        SyncMutation syncMutation = new SyncMutation();
        syncMutation.setClientMutationId(request.mutationId());
        syncMutation.setActorUserId(caller.userId());
        syncMutation.setDeviceId(caller.deviceId().toString());
        syncMutation.setEntityType(request.entityType().name());
        syncMutation.setEntityId(request.entityId());
        syncMutation.setOperation(request.operation().name());
        syncMutation.setStatus(resultResponse.status().name());
        syncMutation.setClientRecordedAt(request.clientRecordedAt());
        syncMutation.setProcessedAt(Instant.now());
        syncMutation.setResponsePayload(objectMapper.writeValueAsString(resultResponse));
        syncMutationRepository.save(syncMutation);
    }
}