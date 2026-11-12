package com.hugo.huds.storage;

import java.time.Instant;

/**
 * A signed link for downloading file directly from object storage.
 * @param  url the link to open with an HTTP GET
 * @param expiresAt when the link stops working
 * @author Meiyan Wang
 * @date 2026-11-12 5:06 p.m.
 */
public record PresignedDownload(String url, Instant expiresAt) {
}
