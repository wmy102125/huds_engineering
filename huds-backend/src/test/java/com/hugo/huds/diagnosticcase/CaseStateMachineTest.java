package com.hugo.huds.diagnosticcase;

import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.diagnosticcase.dto.CaseDisposition;
import com.hugo.huds.diagnosticcase.dto.CaseStateMachine;
import com.hugo.huds.diagnosticcase.dto.WorkflowStatus;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.hugo.huds.diagnosticcase.dto.WorkflowStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The expected matrix is written out again here on purpose, in the form of the spec table,
 * so a mistake in the state machine does not repeat itself in the test.
 */
class CaseStateMachineTest {

    private static final Map<WorkflowStatus, Set<WorkflowStatus>> EXPECTED = new EnumMap<>(WorkflowStatus.class);

    static {
        EXPECTED.put(OPEN, EnumSet.of(DIAGNOSING, ON_HOLD, CANCELLED));
        EXPECTED.put(DIAGNOSING, EnumSet.of(PENDING_AUTHORIZATION, PENDING_PARTS, ON_HOLD, CANCELLED, CLOSED));
        EXPECTED.put(PENDING_AUTHORIZATION, EnumSet.of(IN_REPAIR, PENDING_PARTS, DIAGNOSING, ON_HOLD, CANCELLED, CLOSED));
        EXPECTED.put(PENDING_PARTS, EnumSet.of(IN_REPAIR, ON_HOLD, CANCELLED, CLOSED));
        EXPECTED.put(IN_REPAIR, EnumSet.of(VERIFYING, PENDING_PARTS, DIAGNOSING, ON_HOLD, CLOSED));
        EXPECTED.put(VERIFYING, EnumSet.of(DIAGNOSING, PENDING_PARTS, ON_HOLD, CLOSED));
        EXPECTED.put(ON_HOLD, EnumSet.of(CANCELLED, CLOSED));
        EXPECTED.put(CLOSED, EnumSet.noneOf(WorkflowStatus.class));
        EXPECTED.put(CANCELLED, EnumSet.noneOf(WorkflowStatus.class));
    }

    @Test
    void allowedTargetsMatchTheSpecForEveryState() {
        for (WorkflowStatus from : WorkflowStatus.values()) {
            assertThat(CaseStateMachine.allowedTargets(from, null))
                    .as("targets from %s", from)
                    .isEqualTo(EXPECTED.get(from));
        }
    }

    @Test
    void validateAgreesWithTheMatrixForAllEightyOnePairs() {
        for (WorkflowStatus from : WorkflowStatus.values()) {
            for (WorkflowStatus to : WorkflowStatus.values()) {
                boolean shouldPass = EXPECTED.get(from).contains(to);
                // Give every step a reason and, for closing, a disposition that is allowed everywhere.
                CaseDisposition disposition = to == CLOSED ? CaseDisposition.UNRESOLVED : null;
                if (shouldPass) {
                    assertThatCode(() -> CaseStateMachine.validate(from, to, "because", disposition, null))
                            .as("%s -> %s", from, to)
                            .doesNotThrowAnyException();
                } else {
                    assertThatThrownBy(() -> CaseStateMachine.validate(from, to, "because", disposition, null))
                            .as("%s -> %s", from, to)
                            .isInstanceOfSatisfying(ApplicationException.class,
                                    e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_TRANSITION));
                }
            }
        }
    }

    @Test
    void resumingGoesOnlyToTheStateBeforeTheHold() {
        assertThatCode(() -> CaseStateMachine.validate(ON_HOLD, IN_REPAIR, null, null, IN_REPAIR))
                .doesNotThrowAnyException();
        assertThat(CaseStateMachine.allowedTargets(ON_HOLD, IN_REPAIR)).contains(IN_REPAIR);

        assertInvalidTransition(() -> CaseStateMachine.validate(ON_HOLD, DIAGNOSING, null, null, IN_REPAIR));
        // The history is not known: resuming is not possible.
        assertInvalidTransition(() -> CaseStateMachine.validate(ON_HOLD, IN_REPAIR, null, null, null));
        // Going on hold again from a hold makes no sense.
        assertInvalidTransition(() -> CaseStateMachine.validate(ON_HOLD, ON_HOLD, "x", null, ON_HOLD));
    }

    @Test
    void goingBackPausingAndCancellingNeedAReason() {
        assertInvalidPayload(() -> CaseStateMachine.validate(IN_REPAIR, DIAGNOSING, null, null, null));
        assertInvalidPayload(() -> CaseStateMachine.validate(IN_REPAIR, DIAGNOSING, "  ", null, null));
        assertInvalidPayload(() -> CaseStateMachine.validate(OPEN, ON_HOLD, null, null, null));
        assertInvalidPayload(() -> CaseStateMachine.validate(OPEN, CANCELLED, null, null, null));
        // A step forward does not.
        assertThatCode(() -> CaseStateMachine.validate(OPEN, DIAGNOSING, null, null, null)).doesNotThrowAnyException();
    }

    @Test
    void closingNeedsADispositionThatIsNotCancelled() {
        assertInvalidPayload(() -> CaseStateMachine.validate(VERIFYING, CLOSED, null, null, null));
        assertInvalidPayload(() -> CaseStateMachine.validate(VERIFYING, CLOSED, null, CaseDisposition.CANCELLED, null));
    }

    @Test
    void fixedAndVerifiedIsOnlyPossibleFromVerifying() {
        assertThatCode(() -> CaseStateMachine.validate(VERIFYING, CLOSED, null, CaseDisposition.FIXED_VERIFIED, null))
                .doesNotThrowAnyException();
        assertInvalidPayload(() -> CaseStateMachine.validate(DIAGNOSING, CLOSED, null, CaseDisposition.FIXED_VERIFIED, null));
        assertInvalidPayload(() -> CaseStateMachine.validate(ON_HOLD, CLOSED, null, CaseDisposition.FIXED_VERIFIED, ON_HOLD));
        assertThatCode(() -> CaseStateMachine.validate(DIAGNOSING, CLOSED, null, CaseDisposition.NO_FAULT_FOUND, null))
                .doesNotThrowAnyException();
    }

    @Test
    void aDispositionIsRefusedOnAnyStepThatDoesNotClose() {
        assertInvalidPayload(() -> CaseStateMachine.validate(OPEN, DIAGNOSING, null, CaseDisposition.UNRESOLVED, null));
    }

    @Test
    void aStartedRepairCannotBeCancelled() {
        assertInvalidTransition(() -> CaseStateMachine.validate(IN_REPAIR, CANCELLED, "x", null, null));
        assertInvalidTransition(() -> CaseStateMachine.validate(VERIFYING, CANCELLED, "x", null, null));
    }

    @Test
    void aFinishedCaseCannotChange() {
        assertInvalidTransition(() -> CaseStateMachine.validate(CLOSED, OPEN, null, null, null));
        assertInvalidTransition(() -> CaseStateMachine.validate(CANCELLED, DIAGNOSING, null, null, null));
    }

    private static void assertInvalidTransition(Runnable step) {
        assertThatThrownBy(step::run).isInstanceOfSatisfying(ApplicationException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_TRANSITION));
    }

    private static void assertInvalidPayload(Runnable step) {
        assertThatThrownBy(step::run).isInstanceOfSatisfying(ApplicationException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_PAYLOAD));
    }
}