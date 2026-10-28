package com.hugo.huds.sync.dto;

import com.hugo.huds.sync.dispatcher.SyncEntityType;
import com.hugo.huds.sync.dispatcher.SyncOperation;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @author Meiyan Wang
 * @date 2026-10-20 10:42 a.m.
 */
public record SyncPushMutationRequest(SyncOperation operation,
                                      UUID mutationId,
                                      UUID entityId,
                                      SyncEntityType entityType,
                                      Long rowVersion,
                                      Instant clientRecordedAt,
                                      List<UUID> dependsOn,
                                      JsonNode payload) {
}
