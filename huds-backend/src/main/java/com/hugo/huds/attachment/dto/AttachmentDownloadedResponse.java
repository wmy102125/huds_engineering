package com.hugo.huds.attachment.dto;

import java.time.Instant;

/**
 * @author Meiyan Wang
 * @date 2026-11-13 12:06 p.m.
 */
public record AttachmentDownloadedResponse(String url, Instant expiresAt) {
}
