package com.hugo.huds.attachment.dto;

/**
 * Upload lifecycle of one media file.
 *
 * @author Meiyan Wang
 * @date 2026-11-13 9:45 a.m.
 */
public enum AttachmentUploadStatus {
    PENDING,
    UPLOADING,
    UPLOADED,
    VERIFIED,
    FAILED
}
