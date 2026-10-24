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
     *
     * @param actorUserId Current user id
     * @param syncPushRequest Front end request
     */
    SyncPushResultResponse handle(UUID actorUserId, SyncPushMutationRequest syncPushRequest);


}
