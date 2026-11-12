package com.hugo.huds.appliance.dto;

import java.time.Instant;

/**
 * A short-lived link for downloading the file
 *
 * @author Meiyan Wang
 * @date 2026-11-12 1:32 p.m.
 */
public record ApplianceDocumentDownloadResponse (String url, Instant expiresAt){
}
