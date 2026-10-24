package com.hugo.huds.sync.dto;

import java.time.Instant;
import java.util.List;

/**
 * @author Meiyan Wang
 * @date 2026-10-20 10:43 a.m.
 */
public record SyncPushResponse(Instant serverTime, List<SyncPushResultResponse> results) {
}
