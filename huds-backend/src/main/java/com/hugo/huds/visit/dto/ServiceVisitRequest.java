package com.hugo.huds.visit.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * @author Meiyan Wang
 * @date 2026-11-03 7:01 p.m.
 */
public record ServiceVisitRequest(
        @NotNull
        UUID diagnosticCaseId,
        @NotNull
        UUID technicianId,
        VisitStatus visitStatus,
        /* First visit, later visit or recall. Empty means the server decides on create and "unchanged" on update. */
        VisitKind visitKind,
        /* When the visit is planned for; only meaningful for a PLANNED visit. */
        Instant scheduledAt,
        @Size(max = 2000) String notes
) {
}
