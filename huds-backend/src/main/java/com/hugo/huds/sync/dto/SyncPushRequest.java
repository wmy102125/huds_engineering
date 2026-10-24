package com.hugo.huds.sync.dto;

import java.util.List;
import java.util.UUID;

/**
 * @author Meiyan Wang
 * @date 2026-10-20 2:26 p.m.
 */
public record SyncPushRequest(UUID outboxOwnerUserId, List<SyncPushMutationRequest> mutations) {
}
