package com.hugo.huds.sync.controller;

import com.hugo.huds.auth.support.HudsUserDetails;
import com.hugo.huds.auth.support.RolePermissions;
import com.hugo.huds.sync.dto.SyncPushMutationRequest;
import com.hugo.huds.sync.dto.SyncPushRequest;
import com.hugo.huds.sync.dto.SyncPushResponse;
import com.hugo.huds.sync.service.SyncPushService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Meiyan Wang
 * @date 2026-10-20 10:42 a.m.
 */
@RestController
@RequestMapping("/api/v1/sync")
public class SyncPushController {

    private final SyncPushService syncPushService;

    public SyncPushController(SyncPushService syncPushService) {
        this.syncPushService = syncPushService;
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
                                 @Valid @RequestBody SyncPushRequest request) {
        // The acting user always comes from the authenticated session, never from the request body.
        return syncPushService.push(principal.getUserId(), request);
    }

}
