package com.hugo.huds.sync.controller;

import com.hugo.huds.auth.support.AuthSessionAttributes;
import com.hugo.huds.auth.support.HudsUserDetails;
import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.sync.dispatcher.SyncEntityType;
import com.hugo.huds.sync.dto.SyncCaller;
import com.hugo.huds.sync.dto.SyncPullResponse;
import com.hugo.huds.sync.dto.SyncPushRequest;
import com.hugo.huds.sync.dto.SyncPushResponse;
import com.hugo.huds.sync.service.SyncService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * @author Meiyan Wang
 * @date 2026-10-20 10:42 a.m.
 */
@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {

    private final SyncService syncService;

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    /**
     * Processes a batch of mutations. The HTTP status is 200 whenever the envelope is valid,
     * whatever the outcome of the individual mutations.
     *
     * @param principal the authenticated user
     * @param request   the batch of mutations
     * @return the outcome of every mutation, in the same order
     */
    @PostMapping("/push")
    public SyncPushResponse push(@AuthenticationPrincipal HudsUserDetails principal,
                                 HttpSession session,
                                 @Valid @RequestBody SyncPushRequest request) {
        // The device id was stored in the session at login.
        Object device = session.getAttribute(AuthSessionAttributes.DEVICE_ID);
        if (device == null) {
            throw new ApplicationException(ErrorCode.UNAUTHENTICATED, "The session has no device id. Sign in again.");
        }
        UUID deviceId = UUID.fromString(device.toString());

        Set<String> authorities = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        return syncService.push(new SyncCaller(principal.getUserId(), deviceId, authorities), request);
    }

    /**
     * Downloads the changes of one entity type that happened after the cursor (since, sinceId).
     * Without a cursor, the download starts from the very beginning.
     * Reading needs a login only, which the security configuration already requires.
     *
     * @param entityType the entity type to download
     * @param since      the updatedAt of the last row the device received, or null for the first download
     * @param sinceId    the id of the last row the device received, or null for the first download
     * @param limit      the maximum number of rows in one batch, 500 at most
     * @return one batch of changes and the cursor to continue with
     */
    @GetMapping("/pull")
    public SyncPullResponse pull(@RequestParam("entityType") SyncEntityType entityType,
                                 @RequestParam(value = "since", required = false)
                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
                                 @RequestParam(value = "sinceId", required = false) UUID sinceId,
                                 @RequestParam(value = "limit", required = false) Integer limit) {
        return syncService.pull(entityType, since, sinceId, limit);
    }

}
