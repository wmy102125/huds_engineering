package com.hugo.huds.appliance.controller;

import com.hugo.huds.appliance.dto.*;
import com.hugo.huds.appliance.service.ApplianceDocumentService;
import com.hugo.huds.auth.support.RolePermissions;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * @author Meiyan Wang
 * @date 2026-11-12 1:45 p.m.
 */
@RestController
@RequestMapping("/api/v1/appliance-documents")
public class ApplianceDocumentController {
    private final ApplianceDocumentService applianceDocumentService;

    public ApplianceDocumentController(ApplianceDocumentService applianceDocumentService) {
        this.applianceDocumentService = applianceDocumentService;
    }

    /**
     * Registers a document by its type and reference number. The file is uploaded in a later step.
     */
    @PostMapping
    @PreAuthorize("hasAuthority('" + RolePermissions.APPLIANCE_WRITE + "')")
    public ResponseEntity<ApplianceDocumentResponse> register(@Valid @RequestBody ApplianceDocumentRequest request) {
        ApplianceDocumentResponse response = applianceDocumentService.register(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{applianceDocumentId}")
                .buildAndExpand(response.applianceDocumentId())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    /**
     * Starts the upload of the file: returns a signed link the client sends the file to directly.
     */
    @PostMapping("/{applianceDocumentId}/upload-url")
    @PreAuthorize("hasAuthority('" + RolePermissions.APPLIANCE_WRITE + "')")
    public ResponseEntity<ApplianceDocumentUploadResponse> requestUpload(
            @PathVariable UUID applianceDocumentId,
            @Valid @RequestBody ApplianceDocumentUploadRequest request) {
        return ResponseEntity.ok(applianceDocumentService.requestUpload(applianceDocumentId, request));
    }

    /**
     * Confirms that the file has been uploaded to the signed link. Safe to call again.
     */
    @PostMapping("/{applianceDocumentId}/complete-upload")
    @PreAuthorize("hasAuthority('" + RolePermissions.APPLIANCE_WRITE + "')")
    public ResponseEntity<ApplianceDocumentResponse> completeUpload(@PathVariable UUID applianceDocumentId) {
        return ResponseEntity.ok(applianceDocumentService.completeUpload(applianceDocumentId));
    }

    /**
     * Returns a short-lived link for downloading the file. Any logged-in user can download.
     */
    @GetMapping("/{applianceDocumentId}/download-url")
    public ResponseEntity<ApplianceDocumentDownloadResponse> downloadLink(@PathVariable UUID applianceDocumentId) {
        return ResponseEntity.ok(applianceDocumentService.downloadLink(applianceDocumentId));
    }
}
