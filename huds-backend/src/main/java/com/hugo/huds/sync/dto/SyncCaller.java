package com.hugo.huds.sync.dto;

import java.util.Set;
import java.util.UUID;

/**
 * The authenticated caller of a sync request. It is built by the controller from the session
 * and the request headers, never from the request body.
 *
 * @param userId      the logged-in user
 * @param deviceId    the device that sends the request
 * @param authorities the permissions of the logged-in user
 * @author Meiyan Wang
 * @date 2026-10-26 2:24 a.m.
 */
public record SyncCaller(UUID userId, UUID deviceId, Set<String> authorities) {
}
