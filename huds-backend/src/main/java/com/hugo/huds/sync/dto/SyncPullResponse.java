package com.hugo.huds.sync.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author Meiyan Wang
 * @date 2026-10-26 1:25 p.m.
 */
public record SyncPullResponse(List<?> items, Instant nextSince, UUID nextSinceId, Boolean hasMore) {

    public static SyncPullResponse nullListResponse(Instant nextSince, UUID nextSinceId) {
        return new SyncPullResponse(new ArrayList<>(), nextSince, nextSinceId, false);
    }
}
