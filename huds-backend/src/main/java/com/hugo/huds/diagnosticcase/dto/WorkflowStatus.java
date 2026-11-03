package com.hugo.huds.diagnosticcase.dto;

import java.util.EnumSet;
import java.util.Set;

/**
 * @author Meiyan Wang
 * @date 2026-10-28 11:35 a.m.
 *
*/
public enum WorkflowStatus {
    OPEN,
    DIAGNOSING,
    PENDING_AUTHORIZATION,
    PENDING_PARTS,
    IN_REPAIR,
    VERIFYING,
    ON_HOLD,
    CLOSED,
    CANCELLED;

    private static final Set<WorkflowStatus> FINISHED = EnumSet.of(CLOSED, CANCELLED);

    /** The states a case cannot leave any more. */
    public static Set<WorkflowStatus> finishedStatuses() {
        return EnumSet.copyOf(FINISHED);
    }

    /** True for CLOSED and CANCELLED. */
    public boolean isFinished() {
        return FINISHED.contains(this);
    }
}
