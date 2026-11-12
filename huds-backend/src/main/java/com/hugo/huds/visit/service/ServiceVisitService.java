package com.hugo.huds.visit.service;

import com.hugo.huds.audit.dto.AuditAction;
import com.hugo.huds.audit.service.AuditService;
import com.hugo.huds.audit.service.ChangedFields;
import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.diagnosticcase.dto.CaseStateMachine;
import com.hugo.huds.diagnosticcase.dto.StateEventEntityType;
import com.hugo.huds.diagnosticcase.dto.WorkflowStatus;
import com.hugo.huds.diagnosticcase.entity.DiagnosticCase;
import com.hugo.huds.diagnosticcase.repository.DiagnosticCaseRepository;
import com.hugo.huds.diagnosticcase.service.StateEventService;
import com.hugo.huds.sync.dto.SyncPullResponse;
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
     */
    private static final Set<WorkflowStatus> STARTABLE_CASE_STATUSES =
            EnumSet.of(WorkflowStatus.OPEN, WorkflowStatus.DIAGNOSING, WorkflowStatus.IN_REPAIR);


    private final ServiceVisitRepository serviceVisitRepository;
    private final DiagnosticCaseRepository diagnosticCaseRepository;
    private final AuditService auditService;
    private final StateEventService stateEventService;
    private final VisitSafetyCheckRepository visitSafetyCheckRepository;

    public ServiceVisitService(ServiceVisitRepository serviceVisitRepository, DiagnosticCaseRepository diagnosticCaseRepository, AuditService auditService, StateEventService stateEventService, VisitSafetyCheckRepository visitSafetyCheckRepository) {
        this.serviceVisitRepository = serviceVisitRepository;
        this.diagnosticCaseRepository = diagnosticCaseRepository;
        this.auditService = auditService;
        this.stateEventService = stateEventService;
        this.visitSafetyCheckRepository = visitSafetyCheckRepository;
    }

    @Transactional
    public Long add(@NotNull UUID actorUserId, @NotNull UUID serviceVisitId, @NotNull Instant clientRecordedAt, ServiceVisitRequest request) {
        requireAssignPermission();
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
        if (WorkflowStatus.finishedStatuses().contains(diagnosticCase.getWorkflowStatus())) {
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
        DiagnosticCase diagnosticCase = diagnosticCaseRepository.findById(serviceVisit.getDiagnosticCaseId()).orElseThrow(() ->
                new ApplicationException(ErrorCode.NOT_FOUND, "The diagnostic case does not exist."));

        if (to == VisitStatus.IN_PROGRESS && from == VisitStatus.PLANNED) {
            // The technician starts the visit.
            startVisit(actorUserId, serviceVisit, diagnosticCase, clientRecordedAt);
        }
        // A visit cannot be completed before its safety checks are recorded.
        if (to == VisitStatus.COMPLETED) {
            requireSafetyChecks(serviceVisitId);
        }
        serviceVisit.setVisitStatus(to);
        if (to == VisitStatus.COMPLETED || to == VisitStatus.CANCELLED) {
            serviceVisit.setEndedAt(clientRecordedAt);
        }
        serviceVisitRepository.flush();

        // 5. Write the history entry in the same transaction. The pause reason lives here.
        stateEventService.record(StateEventEntityType.VISIT, serviceVisitId, from.name(), to.name(), reason, actorUserId, clientRecordedAt);
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
     * and an OPEN case moves to DIAGNOSING in the same transaction.
     */
    private void startVisit(UUID actorUserId, ServiceVisit serviceVisit, DiagnosticCase diagnosticCase, Instant clientRecordedAt) {
        requireStartableCase(diagnosticCase);
        serviceVisit.setStartedAt(clientRecordedAt);
        if (diagnosticCase.getWorkflowStatus() == WorkflowStatus.OPEN) {
            CaseStateMachine.validate(WorkflowStatus.OPEN, WorkflowStatus.DIAGNOSING, null, null, null);
            diagnosticCase.setWorkflowStatus(WorkflowStatus.DIAGNOSING);
            diagnosticCaseRepository.flush();
            stateEventService.record(StateEventEntityType.CASE, diagnosticCase.getDiagnosticCaseId(),
                    WorkflowStatus.OPEN.name(), WorkflowStatus.DIAGNOSING.name(), null, actorUserId, clientRecordedAt);
        }
    }

    private void requireStartableCase(DiagnosticCase diagnosticCase) {
        WorkflowStatus caseStatus = diagnosticCase.getWorkflowStatus();
        if (!STARTABLE_CASE_STATUSES.contains(caseStatus)) {
            throw new ApplicationException(ErrorCode.DOMAIN_RULE_VIOLATION,
                    "The case is " + caseStatus + ", a visit cannot be started.");
        }
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

    // Plans a visit: only users with VISIT_ASSIGN (admins) may do it.
    private void requireAssignPermission() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean allowed = authentication != null &&
                authentication.getAuthorities().stream().anyMatch(grantedAuthority -> "VISIT_ASSIGN".equals(grantedAuthority.getAuthority()));
        if (!allowed) {
            throw new ApplicationException(ErrorCode.FORBIDDEN, "Only an administrator can plan or cancel a visit.");
        }
    }

}
