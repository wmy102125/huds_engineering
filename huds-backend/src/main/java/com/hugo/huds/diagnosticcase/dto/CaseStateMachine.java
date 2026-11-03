package com.hugo.huds.diagnosticcase.dto;

import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.hugo.huds.diagnosticcase.dto.WorkflowStatus.*;

/**
 * The only place that knows which case state may follow which (see section 3 of the case module spec).
 * It reads and writes nothing: the service loads the case, calls {@link #validate}, then saves.
 *
 * <p>Two things are not in the static table and are checked in code:
 * <ul>
 *   <li>Resuming: a case on hold may only go back to the state it was in before it was put on hold.
 *       The service reads that state from the history and passes it in.</li>
 *   <li>Closing: the disposition must be present, must not be CANCELLED, and FIXED_VERIFIED is only
 *       possible straight after VERIFYING.</li>
 * </ul>
 */
public final class CaseStateMachine {

    /** One allowed step. A step that goes back, pauses or cancels has to say why. */
    private record Transition(WorkflowStatus to, boolean needsReason) {
    }

    private static final Map<WorkflowStatus, List<Transition>> TABLE = new EnumMap<>(WorkflowStatus.class);

    static {
        TABLE.put(OPEN, List.of(
                go(DIAGNOSING),
                why(ON_HOLD),
                why(CANCELLED)));
        TABLE.put(DIAGNOSING, List.of(
                go(PENDING_AUTHORIZATION),
                go(PENDING_PARTS),
                why(ON_HOLD),
                why(CANCELLED),
                go(CLOSED)));
        TABLE.put(PENDING_AUTHORIZATION, List.of(
                go(IN_REPAIR),
                go(PENDING_PARTS),
                why(DIAGNOSING),
                why(ON_HOLD),
                why(CANCELLED),
                go(CLOSED)));
        TABLE.put(PENDING_PARTS, List.of(
                go(IN_REPAIR),
                why(ON_HOLD),
                why(CANCELLED),
                go(CLOSED)));
        // Once the repair has started the case can no longer be cancelled, only closed.
        TABLE.put(IN_REPAIR, List.of(
                go(VERIFYING),
                why(PENDING_PARTS),
                why(DIAGNOSING),
                why(ON_HOLD),
                go(CLOSED)));
        TABLE.put(VERIFYING, List.of(
                why(DIAGNOSING),
                why(PENDING_PARTS),
                why(ON_HOLD),
                go(CLOSED)));
        // Resuming from ON_HOLD is not listed here: the target depends on the history.
        TABLE.put(ON_HOLD, List.of(
                why(CANCELLED),
                go(CLOSED)));
        TABLE.put(CLOSED, List.of());
        TABLE.put(CANCELLED, List.of());
    }

    private CaseStateMachine() {
    }

    private static Transition go(WorkflowStatus to) {
        return new Transition(to, false);
    }

    private static Transition why(WorkflowStatus to) {
        return new Transition(to, true);
    }

    /** CLOSED and CANCELLED cannot be left. */
    public static boolean isFinished(WorkflowStatus status) {
        return status == CLOSED || status == CANCELLED;
    }

    /** The states a case in {@code from} may move to. Only the state machine rules, not the reason or disposition. */
    public static Set<WorkflowStatus> allowedTargets(WorkflowStatus from, WorkflowStatus statusBeforeHold) {
        Set<WorkflowStatus> targets = EnumSet.noneOf(WorkflowStatus.class);
        for (Transition transition : TABLE.get(from)) {
            targets.add(transition.to());
        }
        if (from == ON_HOLD && isResumable(statusBeforeHold)) {
            targets.add(statusBeforeHold);
        }
        return targets;
    }

    /**
     * Checks one step. Returns normally when it is allowed.
     *
     * @param from             the state the case is in now
     * @param to               the state the user asked for
     * @param reason           why (may be null; required for some steps)
     * @param disposition      how the case ended (only for closing, otherwise null)
     * @param statusBeforeHold the state before the case was put on hold; only used when {@code from} is ON_HOLD
     * @throws ApplicationException INVALID_TRANSITION when the step is not allowed,
     *                           INVALID_PAYLOAD when the step is allowed but something it needs is missing or wrong
     */
    public static void validate(WorkflowStatus from,WorkflowStatus to,String reason,CaseDisposition disposition,WorkflowStatus statusBeforeHold) {
        if (isFinished(from)) {
            throw new ApplicationException(ErrorCode.INVALID_TRANSITION,
                    "The case is " + from + " and cannot change any more.");
        }

        boolean needsReason = false;
        boolean allowed = false;
        for (Transition transition : TABLE.get(from)) {
            if (transition.to() == to) {
                allowed = true;
                needsReason = transition.needsReason();
                break;
            }
        }
        if (!allowed && from == ON_HOLD && to == statusBeforeHold && isResumable(statusBeforeHold)) {
            allowed = true;
        }
        if (!allowed) {
            throw new ApplicationException(ErrorCode.INVALID_TRANSITION,
                    "A case that is " + from + " cannot move to " + to + ".");
        }

        if (needsReason && (reason == null || reason.isBlank())) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD, "Reason is required to move to " + to + ".");
        }
        validateDisposition(from, to, disposition);
    }

    private static void validateDisposition(WorkflowStatus from, WorkflowStatus to, CaseDisposition disposition) {
        if (to != CLOSED) {
            // The server sets CANCELLED itself; nobody else gets to set a disposition.
            if (disposition != null) {
                throw new ApplicationException(ErrorCode.INVALID_PAYLOAD, "Disposition is only allowed when closing a case.");
            }
            return;
        }
        if (disposition == null) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD, "Disposition is required to close a case.");
        }
        if (disposition == CaseDisposition.CANCELLED) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD, "A case is cancelled by cancelling it, not by closing it.");
        }
        if (disposition == CaseDisposition.FIXED_VERIFIED && from != VERIFYING) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD, "FIXED_VERIFIED is only possible after verifying the repair.");
        }
    }

    /** The state before a hold is always an active one: never ON_HOLD, never finished. */
    private static boolean isResumable(WorkflowStatus statusBeforeHold) {
        return statusBeforeHold != null && statusBeforeHold != ON_HOLD && !isFinished(statusBeforeHold);
    }
}