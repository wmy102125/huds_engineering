package com.hugo.huds.attachment.controller;

import com.hugo.huds.auth.support.RolePermissions;
import com.hugo.huds.attachment.dto.AttachmentDownloadedResponse;
import com.hugo.huds.attachment.dto.AttachmentRequest;
import com.hugo.huds.attachment.dto.AttachmentResponse;
import com.hugo.huds.attachment.service.AttachmentService;
import com.hugo.huds.storage.PresignedUpload;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;
/**
 * @author Meiyan Wang
 * @date 2026-11-13 1:51 p.m.
 */
@RestController
@RequestMapping("/api/v1/attachments")
public class AttachmentController {
    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    /**
     * Temporary REST entry used by the tests; the client registers files through the sync push.
     */
    @PostMapping("/{attachmentId}")
    @PreAuthorize("hasAuthority('" + RolePermissions.ATTACHMENT_WRITE + "')")
    public ResponseEntity<AttachmentResponse> register(
            @PathVariable UUID attachmentId,
            @Valid @RequestBody AttachmentRequest request) {
        AttachmentResponse response = attachmentService.register(attachmentId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().build().toUri();
        return ResponseEntity.created(location).body(response);
    }

    /**
     * Returns a signed link the client sends the file to directly. Can be called again
     * while the file is still pending, for example when the earlier link has expired.
     */
    @PostMapping("/{attachmentId}/upload-url")
    @PreAuthorize("hasAuthority('" + RolePermissions.ATTACHMENT_WRITE + "')")
    public ResponseEntity<PresignedUpload> requestUpload(@PathVariable UUID attachmentId) {
        return ResponseEntity.ok(attachmentService.requestUpload(attachmentId));
    }

    /**
     * Confirms that the file has been uploaded to the signed link. Safe to call again.
     */
    @PostMapping("/{attachmentId}/complete-upload")
    @PreAuthorize("hasAuthority('" + RolePermissions.ATTACHMENT_WRITE + "')")
    public ResponseEntity<AttachmentResponse> completeUpload(@PathVariable UUID attachmentId) {
        return ResponseEntity.ok(attachmentService.completeUpload(attachmentId));
    }

    /**
     * Returns a short-lived link for downloading the file. Any logged-in user can download.
     */
    @GetMapping("/{attachmentId}/download-url")
    public ResponseEntity<AttachmentDownloadedResponse> downloadLink(@PathVariable UUID attachmentId) {
        return ResponseEntity.ok(attachmentService.downloadLink(attachmentId));
    }
}
