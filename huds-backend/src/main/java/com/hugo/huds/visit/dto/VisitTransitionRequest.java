package com.hugo.huds.visit.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * @author Meiyan Wang
 * @date 2026-11-05 1:12 p.m.
 */
public record VisitTransitionRequest(@NotNull
                                     VisitStatus toState,
                                     String notes
) {
}
