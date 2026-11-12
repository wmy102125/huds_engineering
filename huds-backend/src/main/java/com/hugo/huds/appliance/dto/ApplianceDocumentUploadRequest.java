package com.hugo.huds.appliance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Describes the file the client is about to upload.
 * The server signs the upload link for exactly size and type.
 *
 * @author Meiyan Wang
 * @date 2026-11-12 1:27 p.m.
 */
public record ApplianceDocumentUploadRequest(@NotBlank(message = "File name is required")
                                             @Size(max = 255, message = "File name is too long")
                                             String fileName,

                                             @NotBlank(message = "MIME type is required")
                                             String mimeType,

                                             @Positive(message = "File size must be greater than zero")
                                             long byteSize,

                                             @NotBlank(message = "SHA-256 is required")
                                             @Pattern(regexp = "^[0-9a-f]{64}$", message = "SHA-256 must be 64 lowercase hex characters")
                                             String sha256Hex) {
}
