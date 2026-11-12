package com.hugo.huds.appliance.dto;

/**
 * Where the file of the a document is in its upload
 * NO_FILE: only the reference number is registered.
 * PENDING: an upload link is issued.
 * UPLOAD: file is in storage and fully described.
 *
 * @author Meiyan Wang
 * @date 2026-11-12 12:27 p.m.
 */
public enum ApplianceDocumentUploadStatus {
    NO_FILE,
    PENDING,
    UPLOADED
}
