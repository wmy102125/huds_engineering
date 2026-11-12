package com.hugo.huds.storage;

import java.time.Instant;
import java.util.Map;

/**
 * A signed link for uploading one file directly to object storage.
 *
 * @param url the link to send the file to with an HTTP PUT
 * @param headers the client must send with the PUT, otherwise the signature does not match.
 * @param expiresAt when the link stops working.
 * @author Meiyan Wang
 * {@code @date} 2026-11-12 12:41 p.m.
 */
public record PresignedUpload(String url, Map<String, String> headers, Instant expiresAt) {
}
