package com.hugo.huds.sync.service;

import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.sync.dispatcher.SyncDispatcher;
import com.hugo.huds.sync.dto.SyncPushMutationRequest;
import com.hugo.huds.sync.dto.SyncPushRequest;
import com.hugo.huds.sync.dto.SyncPushResponse;
import com.hugo.huds.sync.dto.SyncPushResultResponse;
import com.hugo.huds.sync.dto.SyncPushStatus;
import com.hugo.huds.sync.entity.SyncMutation;
import com.hugo.huds.sync.repository.SyncMutationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
public class SyncPushService {

    private static final int MAX_MUTATIONS = 100;

    private final SyncMutationRepository syncMutationRepository;
    private final SyncDispatcher syncDispatcher;
    private final SyncDependencyChecker syncDependencyChecker;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;
    private final TransactionTemplate newTx;

    public SyncPushService(SyncMutationRepository syncMutationRepository, SyncDispatcher syncDispatcher,
                           SyncDependencyChecker syncDependencyChecker, ObjectMapper objectMapper,
                           PlatformTransactionManager txManager) {
        this.syncMutationRepository = syncMutationRepository;
        this.syncDispatcher = syncDispatcher;
        this.syncDependencyChecker = syncDependencyChecker;
        this.objectMapper = objectMapper;
        this.tx = new TransactionTemplate(txManager);
        // Used to record REJECTED outcomes after the business transaction has rolled back.
        this.newTx = new TransactionTemplate(txManager);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Processes a push batch in order, one mutation at a time.
     *
     * @param actorUserId the authenticated user who sends the batch
     * @param request     the batch to process
     * @return the result of every mutation, in the same order
     */
    public SyncPushResponse push(UUID actorUserId, SyncPushRequest request) {
        validateEnvelope(actorUserId, request);

        List<SyncPushResultResponse> results = new ArrayList<>();
        // The outcome of every mutation processed so far, used by later mutations that depend on them.
        Map<UUID, SyncPushStatus> batchResults = new HashMap<>();

        for (SyncPushMutationRequest mutation : request.mutations()) {
            SyncPushResultResponse result = processOne(actorUserId, mutation, batchResults);
            batchResults.put(mutation.mutationId(), result.status());
            results.add(result);
        }
        return new SyncPushResponse(Instant.now(), results);
    }

    /**
     * Processes one mutation: idempotency, dependencies, then the business change in its own transaction.
     */
    private SyncPushResultResponse processOne(UUID actorUserId, SyncPushMutationRequest mutation,
                                              Map<UUID, SyncPushStatus> batchResults) {
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
            // 3. The business change and the ACCEPTED record commit together, or not at all.
            return tx.execute(status -> {
                Long version = syncDispatcher.dispatch(actorUserId, mutation);
                SyncPushResultResponse result = SyncPushResultResponse.accepted(mutation, version);
                saveOutcome(actorUserId, mutation, result);
                return result;
            });
        } catch (ApplicationException ex) {
            // 4. The transaction above has rolled back. Record the rejection in a new transaction.
            return newTx.execute(status -> {
                SyncPushResultResponse rejected = SyncPushResultResponse.rejected(mutation, ex);
                saveOutcome(actorUserId, mutation, rejected);
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
                && Objects.equals(existing.getOperation(), request.syncOperation().name());
        // The same mutation id was used for a different mutation.
        if (!sameMutation) {
            return mutationIdReused(request);
        }

        SyncPushResultResponse stored = objectMapper.readValue(existing.getResponsePayload(), SyncPushResultResponse.class);
        return stored.asReplay();
    }

    private void saveOutcome(UUID actorUserId, SyncPushMutationRequest request, SyncPushResultResponse resultResponse) {
        SyncMutation syncMutation = new SyncMutation();
        syncMutation.setClientMutationId(request.mutationId());
        syncMutation.setActorUserId(actorUserId);
        syncMutation.setDeviceId("TODO");
        syncMutation.setEntityType(request.entityType().name());
        syncMutation.setEntityId(request.entityId());
        syncMutation.setOperation(request.syncOperation().name());
        syncMutation.setStatus(resultResponse.status().name());
        syncMutation.setClientRecordedAt(request.clientRecordedAt());
        syncMutation.setProcessedAt(Instant.now());
        syncMutation.setResponsePayload(objectMapper.writeValueAsString(resultResponse));
        syncMutationRepository.save(syncMutation);
    }
}