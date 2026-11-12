package com.hugo.huds.visit.dto;

import com.hugo.huds.user.reponse.UserBriefResponse;

import java.time.Instant;
import java.util.UUID;

/**
 * @author Meiyan Wang
 * @date 2026-11-06 4:34 p.m.
 */
public record ServiceVisitDetailResponse(UUID serviceVisitId,
                                         UUID diagnosticCaseId,
                                         UserBriefResponse technician,
                                         VisitStatus visitStatus,
                                         VisitKind visitKind,
                                         Instant scheduledAt,
                                         Instant startedAt,
                                         Instant endedAt,
                                         String notes,
                                         Instant createdAt,
                                         Instant updatedAt,
                                         UserBriefResponse creator,
                                         UserBriefResponse editor,
                                         Long rowVersion) {
}
