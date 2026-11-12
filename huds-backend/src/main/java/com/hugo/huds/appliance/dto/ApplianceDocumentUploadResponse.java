package com.hugo.huds.appliance.dto;

import com.hugo.huds.storage.PresignedUpload;

/**
 * @author Meiyan Wang
 * @date 2026-11-12 1:31 p.m.
 */
public record ApplianceDocumentUploadResponse(ApplianceDocumentResponse document,
                                              PresignedUpload upload) {
}
