package com.hugo.huds.sync.controller;

import com.hugo.huds.auth.support.RolePermissions;
import com.hugo.huds.sync.dto.SyncPushMutationRequest;
import com.hugo.huds.sync.dto.SyncPushResponse;
import com.hugo.huds.sync.service.SyncPushService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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


//    @PostMapping("/push")
//    @PreAuthorize("hasAuthority('" + RolePermissions.CUSTOMER_WRITE + "')")
//    public ResponseEntity<SyncPushResponse> push(@Valid @RequestBody SyncPushMutationRequest syncPushRequest) {
//        return ResponseEntity.ok(syncPushService.push(syncPushRequest));
//    }

}
