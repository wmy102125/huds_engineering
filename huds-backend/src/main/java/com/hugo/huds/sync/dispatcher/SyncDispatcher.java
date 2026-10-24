package com.hugo.huds.sync.dispatcher;

import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.sync.dto.SyncPushMutationRequest;
import com.hugo.huds.sync.dto.SyncPushRequest;
import com.hugo.huds.sync.dto.SyncPushResponse;
import com.hugo.huds.sync.dto.SyncPushResultResponse;
import com.hugo.huds.sync.handler.SyncAbstractHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Meiyan Wang
 * @date 2026-10-20 10:59 a.m.
 */
@Slf4j
@Component
public class SyncDispatcher {

    private final Map<SyncEntityType, SyncAbstractHandler> handlerMap;

    public SyncDispatcher(List<SyncAbstractHandler> handlers) {
        this.handlerMap = handlers.stream().collect(Collectors.toMap(
                SyncAbstractHandler::getSyncEntityType,
                Function.identity(),
                (first, second) -> {
                    throw new IllegalStateException("More than one handler is registered for " + first.getSyncEntityType());
                }));
    }

    public SyncPushResponse dispatch(SyncPushRequest pushRequest) {
        List<SyncPushMutationRequest> mutationRequests = pushRequest.mutations();
        if (CollectionUtils.isEmpty(mutationRequests)) {
            log.warn("Sync push from user {} contains no mutations.", pushRequest.outboxOwnerUserId());
            return new SyncPushResponse(Instant.now(), List.of());
        }

        // Valid the whole batch first, so that an invalid batch rejected before anything is processed
        validateBatch(mutationRequests);

        List<SyncPushResultResponse> results = new ArrayList<>();
        mutationRequests.forEach(mutationRequest -> {
            results.add(handlerMap.get(mutationRequest.entityType()).handle(pushRequest.outboxOwnerUserId(), mutationRequest));
        });

        return new SyncPushResponse(Instant.now(),results);

    }

    private void validateBatch(List<SyncPushMutationRequest> mutationRequests) {
        Set<UUID> mulationIds = new HashSet<>();
        for (SyncPushMutationRequest mutationRequest : mutationRequests) {
            // Check if there are any duplicate mutation id in the list
            if (!mulationIds.add(mutationRequest.mutationId())) {
                throw new ApplicationException(ErrorCode.INVALID_PAYLOAD, "The mutation id " + mutationRequest.mutationId() + " is duplicated");
            }
            mulationIds.add(mutationRequest.mutationId());

            // Every mutation record must target an entity type that has a register handler
            if (!handlerMap.containsKey(mutationRequest.entityType())) {
                throw new ApplicationException(ErrorCode.INVALID_PAYLOAD, "Unsupported entityType " + mutationRequest.entityType() + ",mutation id is " + mutationRequest.mutationId());
            }
        }


    }
}
