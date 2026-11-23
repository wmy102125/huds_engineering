package com.hugo.huds.visit.service;

import com.hugo.huds.audit.dto.AuditAction;
import com.hugo.huds.audit.service.AuditService;
import com.hugo.huds.audit.service.ChangedFields;
import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.diagnosticcase.casepart.service.CasePartService;
import com.hugo.huds.diagnosticcase.dto.CaseStateMachine;
import com.hugo.huds.diagnosticcase.dto.StateEventEntityType;
import com.hugo.huds.diagnosticcase.dto.WorkflowStatus;
import com.hugo.huds.diagnosticcase.entity.DiagnosticCase;
import com.hugo.huds.diagnosticcase.repository.DiagnosticCaseRepository;
import com.hugo.huds.diagnosticcase.service.StateEventService;
import com.hugo.huds.sync.dto.SyncPullResponse;
import com.hugo.huds.visit.completion.dto.FinalStatus;
import com.hugo.huds.visit.completion.entity.VisitCompletionRecord;
import com.hugo.huds.visit.completion.repository.VisitCompletionRecordRepository;
import com.hugo.huds.visit.dto.*;
import com.hugo.huds.visit.entity.ServiceVisit;
import com.hugo.huds.visit.repository.ServiceVisitRepository;
import com.hugo.huds.visit.safetycheck.repository.VisitSafetyCheckRepository;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;
import java.util.*;

import static com.hugo.huds.common.util.AuthorityCheck.hasAuthority;
import static com.hugo.huds.common.util.ParamNormalize.blankToNull;

/**
 * @author Meiyan Wang
 * @date 2026-11-03 6:57 p.m.
 */
@Validated
@Service
@Slf4j
@Transactional(readOnly = true)
public class ServiceVisitService {

    private static final String AUDIT_ENTITY_TYPE = "SERVICE_VISIT";
    private static final String VISIT_ASSIGN = "VISIT_ASSIGN";
    /**
     * A visit in one of these statuses is "open": the technician is on site or paused.
     */
    private static final Set<VisitStatus> OPEN_STATUSES =
            EnumSet.of(VisitStatus.PLANNED, VisitStatus.IN_PROGRESS, VisitStatus.PAUSED);
    /**
     * A visit may only be started while the case is in one of these statuses.
     * Starting it moves OPEN to DIAGNOSING and PENDING_PARTS (all parts arrived) to IN_REPAIR.
     */
    private static final Set<WorkflowStatus> STARTABLE_CASE_STATUSES =
            EnumSet.of(WorkflowStatus.OPEN, WorkflowStatus.DIAGNOSING, WorkflowStatus.IN_REPAIR, WorkflowStatus.PENDING_PARTS);
    /**
     * A visit that moves the case on (see applyFinalStatus) can only be completed while the case is in one of these statuses.
     */
    private static final Set<WorkflowStatus> COMPLETABLE_CASE_STATUSES =
            EnumSet.of(WorkflowStatus.DIAGNOSING, WorkflowStatus.IN_REPAIR);


    private final ServiceVisitRepository serviceVisitRepository;
    private final DiagnosticCaseRepository diagnosticCaseRepository;
    private final AuditService auditService;
    private final StateEventService stateEventService;
    private final VisitSafetyCheckRepository visitSafetyCheckRepository;
    private final CasePartService casePartService;
    private final VisitCompletionRecordRepository visitCompletionRecordRepository;

    public ServiceVisitService(ServiceVisitRepository serviceVisitRepository, DiagnosticCaseRepository diagnosticCaseRepository, AuditService auditService, StateEventService stateEventService, VisitSafetyCheckRepository visitSafetyCheckRepository, CasePartService casePartService, VisitCompletionRecordRepository visitCompletionRecordRepository) {
        this.serviceVisitRepository = serviceVisitRepository;
        this.diagnosticCaseRepository = diagnosticCaseRepository;
        this.auditService = auditService;
        this.stateEventService = stateEventService;
        this.visitSafetyCheckRepository = visitSafetyCheckRepository;
        this.casePartService = casePartService;
        this.visitCompletionRecordRepository = visitCompletionRecordRepository;
    }

    @Transactional
    public Long add(@NotNull UUID actorUserId, @NotNull UUID serviceVisitId, @NotNull Instant clientRecordedAt, ServiceVisitRequest request) {
        // An administrator plans any visit; a technician may only plan a follow-up visit for himself or herself.
        if (!hasAuthority(VISIT_ASSIGN)) {
            requireTechnicianFollowUp(actorUserId, request);
        }
        Optional<ServiceVisit> visitOptional = serviceVisitRepository.findById(serviceVisitId);
        if (visitOptional.isPresent()) {
            return visitOptional.get().getRowVersion();
        }
        Optional<ServiceVisit> optionalServiceVisit = serviceVisitRepository.findFirstByDiagnosticCaseIdAndVisitStatusIn(request.diagnosticCaseId(), OPEN_STATUSES);
        if (optionalServiceVisit.isPresent()) {
            throw new ApplicationException(ErrorCode.DUPLICATE_RESOURCE, "There is an visit:" + optionalServiceVisit.get().getServiceVisitId() + " in progress, can't create a new visit.");
        }

        DiagnosticCase diagnosticCase = diagnosticCaseRepository.findById(request.diagnosticCaseId()).orElseThrow(() -> {
            log.error("The diagnostic case id: {} does not exist.", request.diagnosticCaseId());
            return new ApplicationException(ErrorCode.NOT_FOUND, "The diagnostic case does not exist.");
        });
        // A finished case cannot get a new visit.
        if (WorkflowStatus.finishedStatuses().contains(diagnosticCase.getWorkflowStatus()) || WorkflowStatus.ON_HOLD == diagnosticCase.getWorkflowStatus()) {
            throw new ApplicationException(ErrorCode.DOMAIN_RULE_VIOLATION,
                    "The case is " + diagnosticCase.getWorkflowStatus() + ", a visit cannot be planned.");
        }
        VisitKind visitKind = resolveVisitKind(request);
        ServiceVisit serviceVisit = new ServiceVisit();
        BeanUtils.copyProperties(request, serviceVisit);
        serviceVisit.setServiceVisitId(serviceVisitId);
        // A planned visit has not started yet, so startedAt stays empty until the technician starts it.
        serviceVisit.setVisitStatus(VisitStatus.PLANNED);
        serviceVisit.setVisitKind(visitKind);
        serviceVisitRepository.saveAndFlush(serviceVisit);

        // Write the history entry in the same transaction.
        stateEventService.record(StateEventEntityType.VISIT, serviceVisitId, null, VisitStatus.PLANNED.name(), null, actorUserId, clientRecordedAt);

        auditService.recordInTransaction(AuditAction.CREATE, AUDIT_ENTITY_TYPE, serviceVisitId, Map.of());
        return serviceVisit.getRowVersion();
    }

    @Transactional
    public Long update(@NotNull UUID serviceVisitId, ServiceVisitRequest request) {
        requireAssignPermission();
        Optional<ServiceVisit> visitOptional = serviceVisitRepository.findById(serviceVisitId);
        if (!visitOptional.isPresent()) {
            log.error("This service visit id: " + serviceVisitId + "does not exists.");
            throw new ApplicationException(ErrorCode.NOT_FOUND, "This service visit does not exists.");
        }
        // When visit status is not planned , can not change the technician
        ServiceVisit serviceVisit = visitOptional.get();
        // Only a PLANNED visit can be rescheduled or reassigned.
        if (serviceVisit.getVisitStatus() != VisitStatus.PLANNED) {
            throw new ApplicationException(ErrorCode.DOMAIN_RULE_VIOLATION,
                    "The visit is " + serviceVisit.getVisitStatus() + ", it can no longer be changed.");
        }
        Optional<DiagnosticCase> existCase = diagnosticCaseRepository.findById(request.diagnosticCaseId());
        if (!existCase.isPresent()) {
            log.error("This appliance case id: " + request.diagnosticCaseId() + "does not exists.");
            throw new ApplicationException(ErrorCode.NOT_FOUND, "This appliance case does not exists.");
        }

        // When the case status != open, can't change the technician.
        DiagnosticCase diagnosticCase = existCase.get();
        if (WorkflowStatus.finishedStatuses().contains(diagnosticCase.getWorkflowStatus())) {
            throw new ApplicationException(ErrorCode.DOMAIN_RULE_VIOLATION,
                    "The case is " + diagnosticCase.getWorkflowStatus() + ", its visits can no longer be changed.");
        }
        // A visit cannot move to another case.
        if (!Objects.equals(request.diagnosticCaseId(), serviceVisit.getDiagnosticCaseId())) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD, "The case of a visit cannot be changed.");
        }

        ChangedFields changedFields = new ChangedFields()
                .track("technicianId", serviceVisit.getTechnicianId(), request.technicianId())
                .track("visitKind", serviceVisit.getVisitKind(), request.visitKind() == null ? serviceVisit.getVisitKind() : request.visitKind())
                .track("scheduledAt", serviceVisit.getScheduledAt(), request.scheduledAt())
                .track("notes", serviceVisit.getNotes(), request.notes());

        // An empty kind means "unchanged".
        if (request.visitKind() != null) {
            serviceVisit.setVisitKind(request.visitKind());
        }
        serviceVisit.setScheduledAt(request.scheduledAt());
        serviceVisit.setNotes(request.notes());
        serviceVisitRepository.flush();

        auditService.recordInTransaction(AuditAction.UPDATE, AUDIT_ENTITY_TYPE, serviceVisitId, Map.of("changedFields", changedFields.names()));
        return serviceVisit.getRowVersion();
    }

    @Transactional
    public Long transition(@NotNull UUID actorUserId, @NotNull UUID serviceVisitId, @NotNull long expectedRowVersion, @NotNull VisitTransitionRequest request, @NotNull Instant clientRecordedAt) {
        // 1. Load the visit and check the client saw the current version.
        ServiceVisit serviceVisit = serviceVisitRepository.findById(serviceVisitId).orElseThrow(
                () -> new ApplicationException(ErrorCode.NOT_FOUND, "The current service visit id: " + serviceVisitId + " not found.")
        );
        if (!Objects.equals(serviceVisit.getRowVersion(), expectedRowVersion)) {
            throw new ApplicationException(ErrorCode.VERSION_CONFLICT,
                    "The case was changed by someone else.", false, serviceVisit.getRowVersion());
        }


        VisitStatus from = serviceVisit.getVisitStatus();
        VisitStatus to = request.toState();
        String reason = blankToNull(request.notes());

        // 2. Who may do it: the assigned technician, or an administrator. Cancelling is administrator only.
        if (to == VisitStatus.CANCELLED) {
            requireAssignPermission();
        } else if (!Objects.equals(serviceVisit.getTechnicianId(), actorUserId) && !hasAuthority(VISIT_ASSIGN)) {
            throw new ApplicationException(ErrorCode.FORBIDDEN, "This visit is assigned to another technician.");
        }
        // Only needed when resuming from PAUSED.
        VisitStatus statusBeforeHold = null;
        if (from == VisitStatus.PAUSED) {
            String before = stateEventService.stateBeforeHold(
                    StateEventEntityType.VISIT, serviceVisitId, VisitStatus.PAUSED.name());
            statusBeforeHold = before == null ? null : VisitStatus.valueOf(before);
        }

        // Validation
        VisitStateMachine.validate(from, to, reason, statusBeforeHold);
        // 4. Side effects of this step.
        FinalStatus finalStatus = null;
        DiagnosticCase diagnosticCase = diagnosticCaseRepository.findById(serviceVisit.getDiagnosticCaseId()).orElseThrow(() ->
                new ApplicationException(ErrorCode.NOT_FOUND, "The diagnostic case does not exist."));

        if (to == VisitStatus.IN_PROGRESS && from == VisitStatus.PLANNED) {
            // The technician starts the visit.
            startVisit(actorUserId, serviceVisit, diagnosticCase, clientRecordedAt);
        }
        // A visit cannot be completed before its safety checks are recorded.
        if (to == VisitStatus.COMPLETED) {
            requireSafetyChecks(serviceVisitId);
            // The final status of the completion record says what the visit achieved.
            finalStatus = requireFinalStatus(serviceVisitId);
            // The case moves on in the same transaction as the completion.
            applyFinalStatus(actorUserId, diagnosticCase, finalStatus, clientRecordedAt);
        }
        serviceVisit.setVisitStatus(to);
        if (to == VisitStatus.COMPLETED || to == VisitStatus.CANCELLED) {
            serviceVisit.setEndedAt(clientRecordedAt);
        }
        serviceVisitRepository.flush();

        // 5. Write the history entry in the same transaction. The pause reason lives here.
        // The history of a completed visit also says what it achieved.
        String eventReason = finalStatus == null ? reason : "Final status: " + finalStatus.name() + (reason == null ? "" : ". " + reason);
        stateEventService.record(StateEventEntityType.VISIT, serviceVisitId, from.name(), to.name(), eventReason, actorUserId, clientRecordedAt);
        auditService.recordInTransaction(AuditAction.UPDATE, AUDIT_ENTITY_TYPE, serviceVisitId,
                Map.of("from", from.name(), "to", to.name()));
        return serviceVisit.getRowVersion();

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

        List<ServiceVisitRepository.ChangeRow> rows = serviceVisitRepository.findChanges(since, sinceId, limit + 1);
        // There is no change row, return default response
        if (CollectionUtils.isEmpty(rows)) {
            return SyncPullResponse.nullListResponse(since, sinceId);
        }

        boolean hasMore = rows.size() > limit;
        List<ServiceVisitRepository.ChangeRow> page = hasMore ? rows.subList(0, limit) : rows;

        List<ServiceVisitDetailResponse> responses = page.stream().map(row -> new ServiceVisitDetailResponse(
                row.getServiceVisitId(),
                row.getDiagnosticCaseId(),
                row.getTechnician(),
                row.getVisitStatus() == null ? null : VisitStatus.valueOf(row.getVisitStatus()),
                row.getVisitKind() == null ? null : VisitKind.valueOf(row.getVisitKind()),
                row.getScheduledAt(),
                row.getStartedAt(),
                row.getEndedAt(),
                row.getNotes(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                row.getCreator(),
                row.getEditor(),
                row.getRowVersion())).toList();

        ServiceVisitRepository.ChangeRow last = page.get(page.size() - 1);
        return new SyncPullResponse(responses, last.getUpdatedAt(), last.getServiceVisitId(), hasMore);
    }

    /**
     * The technician starts the visit (PLANNED -> IN_PROGRESS): the case must allow a visit, the start time is set,
     * and the case moves on in the same transaction: OPEN to DIAGNOSING, PENDING_PARTS to IN_REPAIR.
     */
    private void startVisit(UUID actorUserId, ServiceVisit serviceVisit, DiagnosticCase diagnosticCase, Instant clientRecordedAt) {
        requireStartableCase(diagnosticCase);
        serviceVisit.setStartedAt(clientRecordedAt);
        WorkflowStatus current = diagnosticCase.getWorkflowStatus();
        if (current == WorkflowStatus.OPEN) {
            // There is no manual step for this one: the first visit starts the diagnosis.
            moveCase(actorUserId, diagnosticCase, WorkflowStatus.DIAGNOSING, "Visit started", clientRecordedAt);
        } else if (current == WorkflowStatus.PENDING_PARTS) {
            CaseStateMachine.validate(WorkflowStatus.PENDING_PARTS, WorkflowStatus.IN_REPAIR, null, null, null);
            moveCase(actorUserId, diagnosticCase, WorkflowStatus.IN_REPAIR, "Visit started, all parts have arrived", clientRecordedAt);
        }
    }

    private void requireStartableCase(DiagnosticCase diagnosticCase) {
        WorkflowStatus caseStatus = diagnosticCase.getWorkflowStatus();
        if (!STARTABLE_CASE_STATUSES.contains(caseStatus)) {
            throw new ApplicationException(ErrorCode.DOMAIN_RULE_VIOLATION,
                    "The case is " + caseStatus + ", a visit cannot be started.");
        }
        // The repair cannot start while a part is still on its way.
        if (caseStatus == WorkflowStatus.PENDING_PARTS && casePartService.hasWaitingParts(diagnosticCase.getDiagnosticCaseId())) {
            throw new ApplicationException(ErrorCode.DOMAIN_RULE_VIOLATION,
                    "Some parts of this case have not arrived yet, the visit cannot be started.");
        }
    }

    /**
     * The completion record has to exist and carry a final status before a visit can be completed.
     */
    private FinalStatus requireFinalStatus(UUID serviceVisitId) {
        return visitCompletionRecordRepository.findByServiceVisitId(serviceVisitId)
                .map(VisitCompletionRecord::getFinalStatus)
                .orElseThrow(() -> new ApplicationException(ErrorCode.DOMAIN_RULE_VIOLATION,
                        "Choose the final status in the completion record of this visit first."));
    }

    /**
     * The final status of the visit decides the next state of the case:
     * <ul>
     *   <li>QUOTE_PENDING_APPROVAL: PENDING_AUTHORIZATION</li>
     *   <li>PARTS_REQUIRED: PENDING_PARTS</li>
     *   <li>OPERATING_NORMALLY, FURTHER_TESTING: IN_REPAIR (an administrator closes the case as fixed and verified)</li>
     *   <li>NO_FAULT_FOUND, REPAIR_DECLINED, NOT_REPAIRABLE: the case is left alone (an administrator closes it with a reason)</li>
     * </ul>
     * A visit that finds something new while the case is IN_REPAIR (another quote, another part) may send it back to a waiting state.
     */
    private void applyFinalStatus(UUID actorUserId, DiagnosticCase diagnosticCase, FinalStatus finalStatus, Instant clientRecordedAt) {
        WorkflowStatus target = switch (finalStatus) {
            case QUOTE_PENDING_APPROVAL -> WorkflowStatus.PENDING_AUTHORIZATION;
            case PARTS_REQUIRED -> WorkflowStatus.PENDING_PARTS;
            case OPERATING_NORMALLY, FURTHER_TESTING -> WorkflowStatus.IN_REPAIR;
            case NO_FAULT_FOUND, REPAIR_DECLINED, NOT_REPAIRABLE -> null;
        };
        if (target == null) {
            return;
        }
        WorkflowStatus current = diagnosticCase.getWorkflowStatus();
        if (!COMPLETABLE_CASE_STATUSES.contains(current)) {
            throw new ApplicationException(ErrorCode.DOMAIN_RULE_VIOLATION,
                    "The case is " + current + ", the visit cannot be completed with the final status " + finalStatus + ".");
        }
        moveCase(actorUserId, diagnosticCase, target, "Visit completed: " + finalStatus.name(), clientRecordedAt);
    }

    /**
     * Moves the case to another state as a consequence of a visit and writes the history entry in the same transaction.
     * Does nothing when the case is already in that state.
     */
    private void moveCase(UUID actorUserId, DiagnosticCase diagnosticCase, WorkflowStatus to, String reason, Instant clientRecordedAt) {
        WorkflowStatus from = diagnosticCase.getWorkflowStatus();
        if (from == to) {
            return;
        }
        diagnosticCase.setWorkflowStatus(to);
        diagnosticCaseRepository.flush();
        stateEventService.record(StateEventEntityType.CASE, diagnosticCase.getDiagnosticCaseId(),
                from.name(), to.name(), reason, actorUserId, clientRecordedAt);
    }

    /**
     * A visit cannot be completed before its safety checks are recorded.
     */
    private void requireSafetyChecks(UUID serviceVisitId) {
        if (!visitSafetyCheckRepository.existsById(serviceVisitId)) {
            throw new ApplicationException(ErrorCode.DOMAIN_RULE_VIOLATION,
                    "Record the safety checks of this visit first.");
        }
    }

    /**
     * Decides the kind of a new visit. When the request does not name one, the first visit of a case is FIRST
     * and every later one is FOLLOW_UP. A RECALL has to be named by the administrator.
     * A visit named FIRST is refused when the case already has a visit that was not cancelled.
     */
    private VisitKind resolveVisitKind(ServiceVisitRequest request) {
        boolean hasEarlierVisit = serviceVisitRepository
                .existsByDiagnosticCaseIdAndVisitStatusNot(request.diagnosticCaseId(), VisitStatus.CANCELLED);
        if (request.visitKind() == null) {
            return hasEarlierVisit ? VisitKind.FOLLOW_UP : VisitKind.FIRST;
        }
        if (request.visitKind() == VisitKind.FIRST && hasEarlierVisit) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD,
                    "The case already has a visit, so this visit cannot be the first visit.");
        }
        return request.visitKind();
    }

    /**
     * A technician (a user without VISIT_ASSIGN) may plan a visit only as a follow-up for himself or herself,
     * and only on a case where he or she has completed a visit already.
     */
    private void requireTechnicianFollowUp(UUID actorUserId, ServiceVisitRequest request) {
        if (!Objects.equals(request.technicianId(), actorUserId)) {
            throw new ApplicationException(ErrorCode.FORBIDDEN, "You can only plan a visit for yourself.");
        }
        if (request.visitKind() != null && request.visitKind() != VisitKind.FOLLOW_UP) {
            throw new ApplicationException(ErrorCode.FORBIDDEN, "You can only plan a follow-up visit.");
        }
        if (!serviceVisitRepository.existsByDiagnosticCaseIdAndTechnicianIdAndVisitStatus(
                request.diagnosticCaseId(), actorUserId, VisitStatus.COMPLETED)) {
            throw new ApplicationException(ErrorCode.FORBIDDEN,
                    "You can plan the next visit after you have completed a visit of this case.");
        }
    }

    // Changes or cancels a planned visit: only users with VISIT_ASSIGN (admins) may do it.
    private void requireAssignPermission() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean allowed = authentication != null &&
                authentication.getAuthorities().stream().anyMatch(grantedAuthority -> "VISIT_ASSIGN".equals(grantedAuthority.getAuthority()));
        if (!allowed) {
            throw new ApplicationException(ErrorCode.FORBIDDEN, "Only an administrator can plan or cancel a visit.");
        }
    }

}