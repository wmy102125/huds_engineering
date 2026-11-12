package com.hugo.huds.storage;

/**
 * What object storage reports about a stored file.
 *
 * @param byteSize the size of the file in bytes.
 * @param contentType the content type with the file.
 * @author Meiyan Wang
 * @date 2026-11-12 12:42 p.m.
 */
public record StoredObject(long byteSize, String contentType) {
}
