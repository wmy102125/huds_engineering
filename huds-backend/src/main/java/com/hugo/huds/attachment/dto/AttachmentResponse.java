package com.hugo.huds.attachment.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * @author Meiyan Wang
 * @date 2026-11-13 10:00 a.m.
 */
public record AttachmentResponse(UUID attachmentId,
                                 AttachmentEntityType entityType,
                                 UUID entityId,
                                 AttachmentKind mediaKind,
                                 AttachmentUploadStatus uploadStatus,
                                 String fileName,
                                 String mimeType,
                                 long byteSize,
                                 long rowVersion,
                                 Instant capturedAt,
                                 Instant createdAt,
                                 Instant updatedAt) {
}
