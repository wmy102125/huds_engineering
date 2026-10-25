package com.hugo.huds.sync.handler;

import com.hugo.huds.sync.dispatcher.SyncEntityType;
import com.hugo.huds.sync.dto.SyncPushMutationRequest;
import com.hugo.huds.sync.dto.SyncPushResponse;
import com.hugo.huds.sync.dto.SyncPushResultResponse;

import java.util.UUID;

/**
 * @author Meiyan Wang
 * @date 2026-10-20 7:11 p.m.
 */
public interface SyncMutationHandler {
    /**
     *
     * @return Get current operation and entity type
     */
    SyncEntityType getSyncEntityType();

    /**
     * Applies the mutation by calling the business service. It runs inside the caller's transaction.
     *
     * @param actorUserId the authenticated user who sent the mutation
     * @param mutation    the mutation to apply
     * @return the row version of the entity after the mutation
     * @throws com.hugo.huds.common.exception.ApplicationException if the business rules reject the mutation
     */
    Long handle(UUID actorUserId, SyncPushMutationRequest mutation);


}
