package com.hugo.huds.sync.handler;

import com.hugo.huds.sync.dispatcher.SyncEntityType;
import com.hugo.huds.sync.dto.SyncPullResponse;
import com.hugo.huds.sync.dto.SyncPushMutationRequest;
import com.hugo.huds.user.service.UserService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * @author Meiyan Wang
 * @date 2026-11-06 6:20 p.m.
 */
@Component
public class UserHandler implements SyncHandler{

    private final UserService userService;

    public UserHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public SyncEntityType getSyncEntityType() {
        return SyncEntityType.USER;
    }

    @Override
    public Long pushHandle(UUID actorUserId, SyncPushMutationRequest mutation) {
        return 0L;
    }

    @Override
    public SyncPullResponse pullHandle(Instant since, UUID sinceId, int limit) {
        return userService.fetchChanges(since, sinceId, limit);
    }
}
