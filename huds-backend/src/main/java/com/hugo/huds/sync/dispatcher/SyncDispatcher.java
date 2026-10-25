package com.hugo.huds.sync.dispatcher;

import com.hugo.huds.sync.dto.SyncPushMutationRequest;
import com.hugo.huds.sync.handler.SyncMutationHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Meiyan Wang
 * @date 2026-10-20 10:59 a.m.
 */
@Slf4j
@Component
public class SyncDispatcher {

    private final Map<SyncEntityType, SyncMutationHandler> handlerMap;

    public SyncDispatcher(List<SyncMutationHandler> handlers) {
        this.handlerMap = handlers.stream().collect(Collectors.toMap(
                SyncMutationHandler::getSyncEntityType,
                Function.identity(),
                (first, second) -> {
                    throw new IllegalStateException(
                            "More than one handler is registered for " + first.getSyncEntityType());
                }));
    }

    /**
     * Checks whether a handler is registered for the entity type.
     *
     * @param entityType the entity type of a mutation
     * @return true if mutations of this type can be handled
     */
    public boolean supports(SyncEntityType entityType) {
        return handlerMap.containsKey(entityType);
    }

    /**
     * Hands the mutation to the handler of its entity type. It runs inside the caller's transaction.
     *
     * @param actorUserId the authenticated user who sent the mutation
     * @param mutation    the mutation to apply
     * @return the row version of the entity after the mutation
     * @throws com.hugo.huds.common.exception.ApplicationException if the business rules reject the mutation
     */
    public Long dispatch(UUID actorUserId, SyncPushMutationRequest mutation) {
        return handlerMap.get(mutation.entityType()).handle(actorUserId, mutation);
    }
}
