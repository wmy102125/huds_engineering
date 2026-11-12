package com.hugo.huds.appliance.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A registered document. The object key is not returned: clients never need to know where a file is stored.
 *
 * @author Meiyan Wang
 * @date 2026-11-12 1:30 p.m.
 */
public record ApplianceDocumentResponse(UUID applianceDocumentId,
                                        ApplianceDocumentType documentType,
                                        String documentRef,
                                        ApplianceDocumentUploadStatus uploadStatus,
                                        String fileName,
                                        String mimeType,
                                        Long byteSize,
                                        long rowVersion,
                                        Instant createdAt,
                                        Instant updatedAt) {
}
