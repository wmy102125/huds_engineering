package com.hugo.huds.attachment.dto;

import com.hugo.huds.storage.PresignedUpload;

/**
 * @author Meiyan Wang
 * @date 2026-11-13 10:58 a.m.
 */
public record AttachmentUploadResponse(AttachmentResponse attachment, PresignedUpload upload) {
}
