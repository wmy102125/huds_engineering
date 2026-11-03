package com.hugo.huds.diagnosticcase.service;

import com.hugo.huds.address.entity.ServiceAddress;
import com.hugo.huds.address.repository.ServiceAddressRepository;
import com.hugo.huds.appliance.entity.Appliance;
import com.hugo.huds.appliance.repository.ApplianceRepository;
import com.hugo.huds.audit.dto.AuditAction;
import com.hugo.huds.audit.service.AuditService;
import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.customer.entity.Customer;
import com.hugo.huds.customer.repository.CustomerRepository;
import com.hugo.huds.diagnosticcase.dto.*;
import com.hugo.huds.diagnosticcase.entity.CaseStateEvent;
import com.hugo.huds.diagnosticcase.entity.DiagnosticCase;
import com.hugo.huds.diagnosticcase.repository.CaseStateEventRepository;
import com.hugo.huds.diagnosticcase.repository.DiagnosticCaseRepository;
import com.hugo.huds.sync.dto.SyncPullResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.*;

import static com.hugo.huds.common.util.ParamNormalize.blankToNull;
import static com.hugo.huds.diagnosticcase.dto.WorkflowStatus.CANCELLED;
import static com.hugo.huds.diagnosticcase.dto.WorkflowStatus.CLOSED;

/**
 * @author Meiyan Wang
 * @date 2026-10-28 6:13 p.m.
 */
@Service
@Transactional(readOnly = true)
public class DiagnosticCaseService {

    private static final String AUDIT_ENTITY_TYPE = "DIAGNOSTIC_CASE";

    private final DiagnosticCaseRepository diagnosticCaseRepository;
    private final CustomerRepository customerRepository;
    private final ServiceAddressRepository serviceAddressRepository;
    private final ApplianceRepository applianceRepository;
    private final CaseNumberService caseNumberService;
    private final AddressSnapshotFactory addressSnapshotFactory;
    private final CaseStateEventRepository caseStateEventRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    private static final TypeReference<Map<String, Object>> SNAPSHOT_TYPE = new TypeReference<>() {
    };

    public DiagnosticCaseService(DiagnosticCaseRepository diagnosticCaseRepository, CustomerRepository customerRepository, ServiceAddressRepository serviceAddressRepository, ApplianceRepository applianceRepository, CaseNumberService caseNumberService, AddressSnapshotFactory addressSnapshotFactory, CaseStateEventRepository caseStateEventRepository, AuditService auditService, ObjectMapper objectMapper) {
        this.diagnosticCaseRepository = diagnosticCaseRepository;
        this.customerRepository = customerRepository;
        this.serviceAddressRepository = serviceAddressRepository;
        this.applianceRepository = applianceRepository;
        this.caseNumberService = caseNumberService;
        this.addressSnapshotFactory = addressSnapshotFactory;
        this.caseStateEventRepository = caseStateEventRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Long add(UUID actorUserId, UUID diagnosticCaseId, DiagnosticCaseRequest request, Instant clientRecordedAt) {

        var existing = diagnosticCaseRepository.findById(diagnosticCaseId);
        if (existing.isPresent()) {
            return existing.get().getRowVersion();
        }

        checkExists(diagnosticCaseId, request.customerId(), request.applianceId(), request.serviceAddressId(), request.relatedCaseId());
        // Whether this appliance case already exists
        Set<WorkflowStatus> finishedStatuses = EnumSet.of(CLOSED, CANCELLED);
        Optional<String> unfinishedCaseNo = diagnosticCaseRepository.findUnfinishedId(request.applianceId(), request.customerId(), finishedStatuses);
        if (unfinishedCaseNo.isPresent()) {
            throw new ApplicationException(ErrorCode.DUPLICATE_RESOURCE, "The appliance case already exists an unfinished case: " + unfinishedCaseNo.get() + " , please finished the unfinished case.");
        }
        DiagnosticCase diagnosticCase = new DiagnosticCase();
        diagnosticCase.setDiagnosticCaseId(diagnosticCaseId);
        diagnosticCase.setCaseNo(caseNumberService.nextCaseNO());
        diagnosticCase.setCustomerId(request.customerId());
        diagnosticCase.setApplianceId(request.applianceId());
        diagnosticCase.setServiceAddressId(request.serviceAddressId());
        diagnosticCase.setRelatedCaseId(request.relatedCaseId());
        diagnosticCase.setWorkflowStatus(WorkflowStatus.OPEN);
        diagnosticCase.setReportedSymptoms(request.reportedSymptoms().trim());
        diagnosticCase.setErrorCodes(blankToNull(request.errorCodes()));
        diagnosticCase.setOpenedAt(clientRecordedAt);
        diagnosticCase.setServiceAddressSnapshot(addressSnapshotFactory.create(request.serviceAddressId(), request.customerId()));
        diagnosticCaseRepository.saveAndFlush(diagnosticCase);

        CaseStateEvent event = new CaseStateEvent();
        event.setDiagnosticCaseId(diagnosticCaseId);
        event.setFromState(null);
        event.setToState(WorkflowStatus.OPEN);
        event.setOccurredAt(Instant.now());
        event.setClientRecordedAt(clientRecordedAt);
        event.setActorUserId(actorUserId);
        caseStateEventRepository.save(event);

        auditService.recordInTransaction(AuditAction.CREATE, AUDIT_ENTITY_TYPE, diagnosticCase.getDiagnosticCaseId(), Map.of());
        return diagnosticCase.getRowVersion();
    }

    @Transactional
    public Long transition(UUID actorUserId, UUID diagnosticCaseId, CaseTransitionRequest request, Instant clientRecordedAt) {
        // 1. Load the case and check the client saw the current version.
        Optional<DiagnosticCase> existing = diagnosticCaseRepository.findById(diagnosticCaseId);
        if (!existing.isPresent()) {
            throw new ApplicationException(ErrorCode.NOT_FOUND, "The current diagnostic case: " + diagnosticCaseId + " not found.");
        }
        // Whether this appliance case already exists
        DiagnosticCase diagnosticCase = existing.get();

        WorkflowStatus from = diagnosticCase.getWorkflowStatus();
        WorkflowStatus to = request.toState();
        // Check status
        WorkflowStatus statusBeforeHold = null;
        if (from == WorkflowStatus.ON_HOLD) {
            // Last event of this case with to_state = ON_HOLD, then take its from_state.
            statusBeforeHold = caseStateEventRepository
                    .findFirstByDiagnosticCaseIdAndToStateOrderByOccurredAtDescCaseStateEventIdDesc(
                            diagnosticCaseId, WorkflowStatus.ON_HOLD)
                    .map(CaseStateEvent::getFromState)
                    .orElse(null);
            // Leaving ON_HOLD: the case is no longer on hold, so the reason must not stay behind.
            diagnosticCase.setHoldReason(null);
        }

        String reason = blankToNull(request.reason());
        // 3. Ask the state machine. It throws INVALID_TRANSITION or INVALID_PAYLOAD if the step is not allowed.
        CaseStateMachine.validate(from, to, reason, request.disposition(), statusBeforeHold);
        // 4. Apply the change and the side effects of this step.
        diagnosticCase.setWorkflowStatus(to);

        switch (to) {
            case ON_HOLD -> diagnosticCase.setHoldReason(reason);
            case CANCELLED -> {
                diagnosticCase.setDisposition(CaseDisposition.CANCELLED);
                diagnosticCase.setClosedAt(Instant.now());
            }
            case CLOSED -> {
                diagnosticCase.setDisposition(request.disposition());
                diagnosticCase.setClosedAt(Instant.now());
            }
            default -> {
            }
        }
        diagnosticCaseRepository.flush();

        // 5. Write the history entry in the same transaction.
        CaseStateEvent event = new CaseStateEvent();
        event.setDiagnosticCaseId(diagnosticCaseId);
        event.setFromState(from);
        event.setToState(to);
        event.setReason(reason);
        event.setOccurredAt(Instant.now());
        event.setClientRecordedAt(clientRecordedAt);
        event.setActorUserId(actorUserId);
        caseStateEventRepository.save(event);

        return diagnosticCase.getRowVersion();
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

        List<DiagnosticCaseRepository.ChangeRow> rows = diagnosticCaseRepository.findChanges(since, sinceId, limit + 1);
        // There is no change row, return default response
        if (CollectionUtils.isEmpty(rows)) {
            return SyncPullResponse.nullListResponse(since, sinceId);
        }

        boolean hasMore = rows.size() > limit;
        List<DiagnosticCaseRepository.ChangeRow> page = hasMore ? rows.subList(0, limit) : rows;

        List<DiagnosticCaseDetailResponse> detailResponses = page.stream().map(changeRow -> {
            return new DiagnosticCaseDetailResponse(
                    changeRow.getDiagnosticCaseId(),
                    changeRow.getCaseNo(),
                    changeRow.getCustomerId(),
                    changeRow.getServiceAddressId(),
                    changeRow.getApplianceId(),
                    // The jsonb column comes back as text; turn it into a map for the response.
                    parseSnapshot(changeRow.getServiceAddressSnapshot()),
                    changeRow.getReportedSymptoms(),
                    changeRow.getErrorCodes(),
                    changeRow.getWorkflowStatus() == null ? null : WorkflowStatus.valueOf(changeRow.getWorkflowStatus()),
                    changeRow.getDisposition() == null ? null : CaseDisposition.valueOf(changeRow.getDisposition()),
                    changeRow.getHoldReason(),
                    changeRow.getRelatedCaseId(),
                    changeRow.getOpenedAt(),
                    changeRow.getClosedAt(),
                    changeRow.getCreatedAt(),
                    changeRow.getUpdatedAt(),
                    changeRow.getCreator(),
                    changeRow.getEditor(),
                    changeRow.getRowVersion());
        }).toList();
        DiagnosticCaseRepository.ChangeRow last = page.get(page.size() - 1);
        Instant nextSince = last.getUpdatedAt();
        UUID nextSinceId = last.getDiagnosticCaseId();
        return new SyncPullResponse(detailResponses, nextSince, nextSinceId, hasMore);
    }

    /**
     * The short form: user ids only, no lookup.
     */
    private DiagnosticCaseResponse toResponse(DiagnosticCase diagnosticCase) {
        return new DiagnosticCaseResponse(
                diagnosticCase.getDiagnosticCaseId(),
                diagnosticCase.getCaseNo(),
                diagnosticCase.getCustomerId(),
                diagnosticCase.getServiceAddressId(),
                diagnosticCase.getApplianceId(),
                parseSnapshot(diagnosticCase.getServiceAddressSnapshot()),
                diagnosticCase.getReportedSymptoms(),
                diagnosticCase.getErrorCodes(),
                diagnosticCase.getWorkflowStatus(),
                diagnosticCase.getDisposition(),
                diagnosticCase.getHoldReason(),
                diagnosticCase.getRelatedCaseId(),
                diagnosticCase.getOpenedAt(),
                diagnosticCase.getClosedAt(),
                diagnosticCase.getRowVersion());
    }

    private Map<String, Object> parseSnapshot(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return objectMapper.readValue(json, SNAPSHOT_TYPE);
    }

    private void checkExists(UUID diagnosticCaseId, UUID customerId, UUID applianceId, UUID serviceAddressId, UUID relatedCaseId) {

        // These must exist; otherwise the database would refuse the row with a foreign key error (HTTP 500).

        if (!customerRepository.existsById(customerId)) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD, "The customer: " + customerId + " does not exist.");
        }

        Appliance appliance = applianceRepository.findById(applianceId).orElseThrow(() ->
                new ApplicationException(ErrorCode.INVALID_PAYLOAD,"The appliance: " + applianceId + " does not exist."));
        if (!appliance.getCustomerId().equals(customerId)) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD,"The appliance does not belong to this customer.");
        }
        ServiceAddress serviceAddress = serviceAddressRepository.findById(serviceAddressId).orElse(null);
        if (Objects.isNull(serviceAddress)) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD, "The service address: " + serviceAddressId + " does not exist.");
        }

        if(!serviceAddress.getCustomerId().equals(customerId)){
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD, "The service address: " + serviceAddress.getAddressLine1() + " is not the current customer's address");
        }

        if (relatedCaseId != null && (relatedCaseId.equals(diagnosticCaseId) || !diagnosticCaseRepository.existsById(relatedCaseId))) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD, "The related case: " + relatedCaseId + " does not exist.");
        }
    }
}
