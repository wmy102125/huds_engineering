package com.hugo.huds.visit.dto;

import java.util.EnumSet;
import java.util.Set;

/**
 * @author Meiyan Wang
 * @date 2026-10-28 6:41 p.m.
 */
public enum VisitStatus {
    PLANNED,
    IN_PROGRESS,
    PAUSED,
    COMPLETED,
    CANCELLED;

    private static final Set<VisitStatus> OPEN = EnumSet.of(IN_PROGRESS, PAUSED);
    private static final Set<VisitStatus> FINISHED = EnumSet.of(COMPLETED, CANCELLED);

    public boolean isOpen() {
        return OPEN.contains(this);
    }

    public boolean isFinished() {
        return FINISHED.contains(this);
    }

    public static Set<VisitStatus> creatableStatuses() {
        return EnumSet.of(PLANNED, IN_PROGRESS);
    }
}
