package com.hugo.huds.sync.handler;

import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.sync.dto.SyncPushMutationRequest;
import com.hugo.huds.sync.dto.SyncPushResultResponse;
import com.hugo.huds.sync.entity.SyncMutation;
import com.hugo.huds.sync.repository.SyncMutationRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.hugo.huds.sync.dto.SyncPushResultResponse.mutationIdReused;

/**
 *
 * @author Meiyan Wang
 * @date 2026-10-20 6:43 p.m.
 */
public abstract class SyncAbstractHandler implements SyncMutationHandler {

    private final SyncMutationRepository syncMutationRepository;
    public final ObjectMapper objectMapper;
    private final TransactionTemplate tx;
    private final TransactionTemplate newTx;
    private Validator validator;

    protected SyncAbstractHandler(SyncMutationRepository syncMutationRepository, ObjectMapper objectMapper,
                                  PlatformTransactionManager txManager, Validator validator) {
        this.syncMutationRepository = syncMutationRepository;
        this.objectMapper = objectMapper;
        this.tx = new TransactionTemplate(txManager);
        //Used to record REJECT outcomes after the business transaction has rolled back.
        this.newTx = new TransactionTemplate(txManager);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.validator = validator;
    }

    // getSyncEntityType() is no longer declared here:
    // it is inherited from the interface and implemented by the concrete handler


    @Override
    public SyncPushResultResponse handle(UUID actorUserId, SyncPushMutationRequest mutationRequest) {

        // 1. Idempotency: was this mutation already processed for this user?
        Optional<SyncMutation> existing = syncMutationRepository.findByActorUserIdAndClientMutationId(actorUserId, mutationRequest.mutationId());

        if (existing.isPresent()) {
            return replay(existing.get(), mutationRequest);
        }

        try {
            // 2.Business change and the ACCEPTED record commit together, or not at all.
            return tx.execute(status -> {
                SyncPushResultResponse result = doHandle(mutationRequest);
                saveOutcome(actorUserId, mutationRequest, result);
                return result;
            });
        } catch (ApplicationException ex) {
            return newTx.execute(status -> {
                // 3. The transaction above has rolled back. Record the rejection in a new transaction.
                SyncPushResultResponse rejected = SyncPushResultResponse.rejected(mutationRequest, ex);
                saveOutcome(actorUserId, mutationRequest, rejected);
                return rejected;
            });
        }
        // Any other exception(database down, bug) propagates: nothing is recorded and the clients retries.
    }

    private SyncPushResultResponse replay(SyncMutation existing, SyncPushMutationRequest request) {
        boolean sameMutation = Objects.equals(existing.getEntityType(), request.entityType().name())
                && existing.getEntityId().equals(request.entityId())
                && Objects.equals(existing.getOperation(), request.syncOperation().name());
        // The same mutationId was used for different mutation
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

    /**
     * Reads the mutation payload into a request object and validates it.
     */
    protected <T> T readPayload(SyncPushMutationRequest request, Class<T> type) {
        JsonNode payload = request.payload();
        if (payload == null || payload.isNull()) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD, "Payload is required.");
        }
        T value;
        try {
            value = objectMapper.treeToValue(payload, type);
        } catch (RuntimeException ex) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD, "Payload has an invalid format.");
        }
        Set<ConstraintViolation<T>> violations = validator.validate(value);
        if (!violations.isEmpty()) {
            ConstraintViolation<T> first = violations.iterator().next();
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD,
                    first.getPropertyPath() + " " + first.getMessage());
        }
        return value;
    }

    /**
     * Entity-specific business change. Returns an ACCEPTED result. Throws ApplicationException to reject.
     *
     * @param request Front end request
     * @return Returns an ACCEPTED result.
     */
    protected abstract SyncPushResultResponse doHandle(SyncPushMutationRequest request);
}
