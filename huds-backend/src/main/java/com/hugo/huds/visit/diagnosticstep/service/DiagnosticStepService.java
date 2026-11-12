package com.hugo.huds.visit.diagnosticstep.service;

import com.hugo.huds.audit.dto.AuditAction;
import com.hugo.huds.audit.service.AuditService;
import com.hugo.huds.audit.service.ChangedFields;
import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.common.util.AuthorityCheck;
import com.hugo.huds.sync.dto.SyncPullResponse;
import com.hugo.huds.visit.diagnosticstep.dto.DiagnosticStepDetailResponse;
import com.hugo.huds.visit.diagnosticstep.dto.DiagnosticStepRequest;
import com.hugo.huds.visit.diagnosticstep.dto.PowerState;
import com.hugo.huds.visit.diagnosticstep.dto.StepResult;
import com.hugo.huds.visit.diagnosticstep.entity.DiagnosticStep;
import com.hugo.huds.visit.diagnosticstep.repository.DiagnosticStepRepository;
import com.hugo.huds.visit.dto.VisitStatus;
import com.hugo.huds.visit.entity.ServiceVisit;
import com.hugo.huds.visit.repository.ServiceVisitRepository;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.hugo.huds.common.util.ParamNormalize.blankToNull;

/**
 * Business operations for the diagnostic steps of a visit (section 3 of the diagnostic form).
 *
 * <p>A visit has many steps, each with its own id and a number that is unique within the visit.
 * They are written by the assigned technician (or an administrator) while the visit is on site.
 * A finished visit is read only. A step is never deleted: a wrong step is corrected by editing it.</p>
 *
 * @author Meiyan Wang
 * @date 2026-11-03 6:57 p.m.
 */
@Validated
@Service
@Transactional(readOnly = true)
public class DiagnosticStepService {

    private static final String AUDIT_ENTITY_TYPE = "DIAGNOSTIC_STEP";
    private static final String VISIT_ASSIGN = "VISIT_ASSIGN";

    /** The steps are written on site: while the visit is in progress or paused. */
    private static final Set<VisitStatus> WRITABLE_VISIT_STATUSES =
            EnumSet.of(VisitStatus.IN_PROGRESS, VisitStatus.PAUSED);

    private final DiagnosticStepRepository diagnosticStepRepository;
    private final ServiceVisitRepository serviceVisitRepository;
    private final AuditService auditService;

    public DiagnosticStepService(DiagnosticStepRepository diagnosticStepRepository,
                                 ServiceVisitRepository serviceVisitRepository,
                                 AuditService auditService) {
        this.diagnosticStepRepository = diagnosticStepRepository;
        this.serviceVisitRepository = serviceVisitRepository;
        this.auditService = auditService;
    }

    /**
     * Adds a step to a visit, with the id chosen by the caller (the client generates it offline).
     *
     * @param actorUserId      the user who sends the change
     * @param diagnosticStepId the id of the new step
     * @param request          the step details
     * @return the row version of the new row
     */
    @Transactional
    public Long add(@NotNull UUID actorUserId, @NotNull UUID diagnosticStepId, @NotNull DiagnosticStepRequest request) {
        // A retried request with the same id: the row already exists, so return it as is.
        Optional<DiagnosticStep> existing = diagnosticStepRepository.findById(diagnosticStepId);
        if (existing.isPresent()) {
            return existing.get().getRowVersion();
        }

        requireWritableVisit(actorUserId, request.serviceVisitId());

        // The number is unique within the visit.
        Optional<DiagnosticStep> sameNumber =
                diagnosticStepRepository.findByServiceVisitIdAndStepNo(request.serviceVisitId(), request.stepNo());
        if (sameNumber.isPresent()) {
            throw ApplicationException.duplicate("The visit already has a step " + request.stepNo() + ".",
                    sameNumber.get().getDiagnosticStepId());
        }

        DiagnosticStep step = new DiagnosticStep();
        step.setDiagnosticStepId(diagnosticStepId);
        step.setServiceVisitId(request.serviceVisitId());
        step.setStepNo(request.stepNo());
        step.setHypothesis(request.hypothesis().trim());
        step.setTestTarget(blankToNull(request.testTarget()));
        step.setPowerState(request.powerState());
        step.setExpected(blankToNull(request.expected()));
        step.setActual(blankToNull(request.actual()));
        step.setResult(request.result());
        step.setNextStep(blankToNull(request.nextStep()));
        // saveAndFlush so that the row_version and audit timestamps are populated before they are read.
        diagnosticStepRepository.saveAndFlush(step);
        auditService.recordInTransaction(AuditAction.CREATE, AUDIT_ENTITY_TYPE, diagnosticStepId, Map.of());
        return step.getRowVersion();
    }

    /**
     * Changes a step of a visit that is on site, provided the caller has seen the current version.
     * The visit and the number of a step never change. Every other field is replaced as sent.
     *
     * @param actorUserId      the user who sends the change
     * @param diagnosticStepId the id of the step
     * @param rowVersion       the version on which the caller based the change
     * @param request          the new values
     * @return the new row version
     */
    @Transactional
    public Long update(@NotNull UUID actorUserId, @NotNull UUID diagnosticStepId, @NotNull Long rowVersion,
                       @NotNull DiagnosticStepRequest request) {
        DiagnosticStep step = diagnosticStepRepository.findById(diagnosticStepId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.NOT_FOUND,
                        "Diagnostic step " + diagnosticStepId + " not found."));
        if (!Objects.equals(step.getRowVersion(), rowVersion)) {
            throw new ApplicationException(ErrorCode.VERSION_CONFLICT,
                    "The step was changed by someone else.", false, step.getRowVersion());
        }
        if (!Objects.equals(step.getServiceVisitId(), request.serviceVisitId())
                || !Objects.equals(step.getStepNo(), request.stepNo())) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD,
                    "The visit and the number of a step cannot be changed.");
        }
        requireWritableVisit(actorUserId, step.getServiceVisitId());

        String hypothesis = request.hypothesis().trim();
        String testTarget = blankToNull(request.testTarget());
        PowerState powerState = request.powerState();
        String expected = blankToNull(request.expected());
        String actual = blankToNull(request.actual());
        StepResult result = request.result();
        String nextStep = blankToNull(request.nextStep());

        // Compare before applying the change. Only the field names go to the audit trail.
        ChangedFields changedFields = new ChangedFields()
                .track("hypothesis", step.getHypothesis(), hypothesis)
                .track("testTarget", step.getTestTarget(), testTarget)
                .track("powerState", step.getPowerState(), powerState)
                .track("expected", step.getExpected(), expected)
                .track("actual", step.getActual(), actual)
                .track("result", step.getResult(), result)
                .track("nextStep", step.getNextStep(), nextStep);

        step.setHypothesis(hypothesis);
        step.setTestTarget(testTarget);
        step.setPowerState(powerState);
        step.setExpected(expected);
        step.setActual(actual);
        step.setResult(result);
        step.setNextStep(nextStep);
        // Flush so that the row_version is the new one when it is read.
        diagnosticStepRepository.flush();
        auditService.recordInTransaction(AuditAction.UPDATE, AUDIT_ENTITY_TYPE, diagnosticStepId,
                Map.of("changedFields", changedFields.names()));
        return step.getRowVersion();
    }

    /**
     * Searches the change rows by since, sinceId and limit.
     *
     * @param since   the change rows start time
     * @param sinceId the change rows start id
     * @param limit   this search batch max size number
     * @return matching rows
     */
    public SyncPullResponse fetchChanges(Instant since, UUID sinceId, int limit) {
        List<DiagnosticStepRepository.ChangeRow> rows = diagnosticStepRepository.findChanges(since, sinceId, limit + 1);
        // There is no change row, return default response
        if (CollectionUtils.isEmpty(rows)) {
            return SyncPullResponse.nullListResponse(since, sinceId);
        }

        boolean hasMore = rows.size() > limit;
        List<DiagnosticStepRepository.ChangeRow> page = hasMore ? rows.subList(0, limit) : rows;

        List<DiagnosticStepDetailResponse> responses = page.stream().map(row -> new DiagnosticStepDetailResponse(
                row.getDiagnosticStepId(),
                row.getServiceVisitId(),
                row.getStepNo(),
                row.getHypothesis(),
                row.getTestTarget(),
                row.getPowerState() == null ? null : PowerState.valueOf(row.getPowerState()),
                row.getExpected(),
                row.getActual(),
                row.getResult() == null ? null : StepResult.valueOf(row.getResult()),
                row.getNextStep(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                row.getCreator(),
                row.getEditor(),
                row.getRowVersion())).toList();

        DiagnosticStepRepository.ChangeRow last = page.get(page.size() - 1);
        return new SyncPullResponse(responses, last.getUpdatedAt(), last.getDiagnosticStepId(), hasMore);
    }

    /**
     * The visit must exist and be on site (in progress or paused), and the caller must be the assigned
     * technician or an administrator.
     */
    private void requireWritableVisit(UUID actorUserId, UUID serviceVisitId) {
        ServiceVisit visit = serviceVisitRepository.findById(serviceVisitId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.INVALID_PAYLOAD,
                        "The visit: " + serviceVisitId + " does not exist."));
        if (!Objects.equals(visit.getTechnicianId(), actorUserId) && !AuthorityCheck.hasAuthority(VISIT_ASSIGN)) {
            throw new ApplicationException(ErrorCode.FORBIDDEN, "This visit is assigned to another technician.");
        }
        if (!WRITABLE_VISIT_STATUSES.contains(visit.getVisitStatus())) {
            throw new ApplicationException(ErrorCode.DOMAIN_RULE_VIOLATION,
                    "The visit is " + visit.getVisitStatus() + ", its diagnostic steps can only be written while the "
                            + "technician is on site.");
        }
    }


}
