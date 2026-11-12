package com.hugo.huds.visit.dto;


import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.diagnosticcase.dto.CaseDisposition;
import com.hugo.huds.diagnosticcase.dto.CaseStateMachine;
import com.hugo.huds.diagnosticcase.dto.WorkflowStatus;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.hugo.huds.diagnosticcase.dto.WorkflowStatus.ON_HOLD;
import static com.hugo.huds.visit.dto.VisitStatus.*;

/**
 * @author Meiyan Wang
 * @date 2026-11-04 10:04 a.m.
 */
public final class VisitStateMachine {

    private record Transition(VisitStatus to, boolean needsReason) {
    }

    public static final Map<VisitStatus, List<Transition>> TABLE = new EnumMap<>(VisitStatus.class);

    static {
        // The technician arrives: PLANNED -> SAFETY_CHECK. The work starts after the safety checks are recorded.
        TABLE.put(VisitStatus.PLANNED, List.of(go(IN_PROGRESS), why(PAUSED), why(CANCELLED)));
        TABLE.put(VisitStatus.IN_PROGRESS, List.of(why(PAUSED), go(COMPLETED), why(CANCELLED)));
        // Resuming goes back to the state before the pause (see validate); a paused visit can also be completed.
        TABLE.put(VisitStatus.PAUSED, List.of(go(COMPLETED)));
        TABLE.put(VisitStatus.COMPLETED, List.of());
        TABLE.put(VisitStatus.CANCELLED, List.of());
    }

    /**
     * CLOSED and CANCELLED cannot be left.
     */
    public static boolean isFinished(VisitStatus status) {
        return status == COMPLETED || status == CANCELLED;
    }

    private static Transition go(VisitStatus status) {
        return new Transition(status, false);
    }

    private static Transition why(VisitStatus status) {
        return new Transition(status, true);
    }

    public static void validate(VisitStatus from, VisitStatus to, String reason, VisitStatus statusBeforePaused) {
        if (isFinished(from)) {
            throw new ApplicationException(ErrorCode.INVALID_TRANSITION,
                    "The visit is " + from + " and cannot change any more.");
        }

        boolean needsReason = false;
        boolean allowed = false;
        for (Transition status : TABLE.get(from)) {
            if (status.to == to) {
                allowed = true;
                needsReason = status.needsReason();
                break;
            }
        }
        if (!allowed && from == PAUSED && to == statusBeforePaused && isResumable(statusBeforePaused)) {
            allowed = true;
        }
        if (!allowed) {
            throw new ApplicationException(ErrorCode.INVALID_TRANSITION,
                    "A case that is " + from + " cannot move to " + to + ".");
        }

        if (needsReason && (reason == null || reason.isBlank())) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD, "Reason is required to move to " + to + ".");
        }
    }

    /** The state before a paused is always an active one: never PAUSED, never finished. */
    private static boolean isResumable(VisitStatus statusBeforePaused) {
        return statusBeforePaused != null && statusBeforePaused != PAUSED && !isFinished(statusBeforePaused);
    }
}
