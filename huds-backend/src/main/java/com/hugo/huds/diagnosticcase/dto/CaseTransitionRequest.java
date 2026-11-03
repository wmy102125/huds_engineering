package com.hugo.huds.diagnosticcase.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * @author Meiyan Wang
 * @date 2026-10-28 12:14 p.m.
 */
public record CaseTransitionRequest(@NotNull(message = "ToState is required")
                                    WorkflowStatus toState,
                                    String reason,
                                    CaseDisposition disposition) {
}
