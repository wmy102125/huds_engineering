package com.hugo.huds.attachment.dto;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.UUID;

/**
 * One file the client wants to upload.
 * @author Meiyan Wang
 * @date 2026-11-13 11:12 a.m.
 */
public record AttachmentRequest(
        @NotNull
        AttachmentEntityType entityType,
        @NotNull
        UUID entityId,
        @NotBlank(message = "File name is required")
        @Size(max = 255, message = "File name is too long")
        String fileName,
        @NotBlank(message = "MIME type is required")
        String mimeType,
        @Positive(message = "File size must be greater than zero")
        long byteSize,
        @NotBlank(message = "SHA-256 is required")
        @Pattern(regexp = "^[0-9a-f]{64}$", message = "SHA-256 must be 64 lowercase hex characters")
        String sha256Hex,
        Instant capturedAt
) {
}
