package com.hugo.huds.diagnosticcase.service;

import com.hugo.huds.address.entity.ServiceAddress;
import com.hugo.huds.address.repository.ServiceAddressRepository;
import com.hugo.huds.appliance.entity.Appliance;
import com.hugo.huds.appliance.repository.ApplianceRepository;
import com.hugo.huds.audit.dto.AuditAction;
import com.hugo.huds.audit.service.AuditService;
import com.hugo.huds.audit.service.ChangedFields;
import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.common.util.JsonFieldSupport;
import com.hugo.huds.customer.repository.CustomerRepository;
import com.hugo.huds.diagnosticcase.dto.*;
import com.hugo.huds.diagnosticcase.entity.DiagnosticCase;
import com.hugo.huds.diagnosticcase.repository.DiagnosticCaseRepository;
import com.hugo.huds.sync.dto.SyncPullResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.*;

import static com.hugo.huds.common.util.ParamNormalize.blankToNull;

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
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final StateEventService stateEventService;
    private final JsonFieldSupport jsonFieldSupport;

    private static final TypeReference<Map<String, Object>> SNAPSHOT_TYPE = new TypeReference<>() {
    };

    public DiagnosticCaseService(DiagnosticCaseRepository diagnosticCaseRepository, CustomerRepository customerRepository, ServiceAddressRepository serviceAddressRepository, ApplianceRepository applianceRepository, CaseNumberService caseNumberService, AddressSnapshotFactory addressSnapshotFactory, AuditService auditService, ObjectMapper objectMapper, StateEventService stateEventService, JsonFieldSupport jsonFieldSupport) {
        this.diagnosticCaseRepository = diagnosticCaseRepository;
        this.customerRepository = customerRepository;
        this.serviceAddressRepository = serviceAddressRepository;
        this.applianceRepository = applianceRepository;
        this.caseNumberService = caseNumberService;
        this.addressSnapshotFactory = addressSnapshotFactory;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.stateEventService = stateEventService;
        this.jsonFieldSupport = jsonFieldSupport;
    }

    @Transactional
    public Long add(UUID actorUserId, UUID diagnosticCaseId, DiagnosticCaseRequest request, Instant clientRecordedAt) {

        var existing = diagnosticCaseRepository.findById(diagnosticCaseId);
        if (existing.isPresent()) {
            return existing.get().getRowVersion();
        }

        // Check the JSON fields first, so a bad payload is refused before anything is read or written.
        String secondarySymptoms = jsonFieldSupport.arrayText(request.secondarySymptoms(), "secondarySymptoms");
        String failureTiming = jsonFieldSupport.objectText(request.failureTiming(), "failureTiming");

        checkExists(diagnosticCaseId, request.customerId(), request.applianceId(), request.serviceAddressId(), request.relatedCaseId());
        // Whether this appliance case already exists
        Set<WorkflowStatus> finishedStatuses = WorkflowStatus.finishedStatuses();
        Optional<UUID> unfinishedCaseId = diagnosticCaseRepository.findUnfinishedId(request.applianceId(), request.customerId(), finishedStatuses);
        unfinishedCaseId.ifPresent(existingId -> {
            throw new ApplicationException(ErrorCode.DUPLICATE_RESOURCE,
                    "The appliance already has an unfinished case, existingId: "+ existingId);
        });
        DiagnosticCase diagnosticCase = new DiagnosticCase();
        diagnosticCase.setDiagnosticCaseId(diagnosticCaseId);
        diagnosticCase.setCaseNo(caseNumberService.nextCaseNO());
        diagnosticCase.setCustomerId(request.customerId());
        diagnosticCase.setApplianceId(request.applianceId());
        diagnosticCase.setServiceAddressId(request.serviceAddressId());
        diagnosticCase.setRelatedCaseId(request.relatedCaseId());
        diagnosticCase.setWorkflowStatus(WorkflowStatus.OPEN);
        diagnosticCase.setReportedSymptoms(request.reportedSymptoms().trim());
        diagnosticCase.setPrimarySymptom(request.primarySymptom());
        diagnosticCase.setSecondarySymptoms(secondarySymptoms);
        diagnosticCase.setFailureTiming(failureTiming);
        diagnosticCase.setOpenedAt(clientRecordedAt);
        diagnosticCase.setServiceAddressSnapshot(addressSnapshotFactory.create(request.serviceAddressId(), request.customerId()));
        diagnosticCaseRepository.saveAndFlush(diagnosticCase);

        // Write the history entry in the same transaction.
        stateEventService.record(StateEventEntityType.CASE, diagnosticCaseId, null, WorkflowStatus.OPEN.name(), null, actorUserId, clientRecordedAt);

        auditService.recordInTransaction(AuditAction.CREATE, AUDIT_ENTITY_TYPE, diagnosticCase.getDiagnosticCaseId(), Map.of());
        return diagnosticCase.getRowVersion();
    }

    /**
     * Changes the fields of an open case that the technician fills in: the reported symptoms and the
     * symptom classification of the diagnostic form. The links (customer, appliance, address, related case)
     * never change. An empty classification field in the request means "unchanged".
     *
     * @param diagnosticCaseId   the id of the case
     * @param expectedRowVersion the version on which the caller based the change
     * @param request            the new values
     * @return the new row version
     */
    @Transactional
    public Long update(UUID diagnosticCaseId, Long expectedRowVersion, DiagnosticCaseRequest request) {
        // Check the JSON fields first, so a bad payload is refused before anything is read or written.
        String secondarySymptoms = request.secondarySymptoms() == null ? null
                : jsonFieldSupport.arrayText(request.secondarySymptoms(), "secondarySymptoms");
        String failureTiming = request.failureTiming() == null ? null
                : jsonFieldSupport.objectText(request.failureTiming(), "failureTiming");

        DiagnosticCase diagnosticCase = diagnosticCaseRepository.findById(diagnosticCaseId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.NOT_FOUND,
                        "The current diagnostic case: " + diagnosticCaseId + " not found."));

        if (!Objects.equals(diagnosticCase.getRowVersion(), expectedRowVersion)) {
            throw new ApplicationException(ErrorCode.VERSION_CONFLICT,
                    "The case was changed by someone else.", false, diagnosticCase.getRowVersion());
        }
        // A finished case is read only.
        if (WorkflowStatus.finishedStatuses().contains(diagnosticCase.getWorkflowStatus())) {
            throw new ApplicationException(ErrorCode.DOMAIN_RULE_VIOLATION,
                    "The case is " + diagnosticCase.getWorkflowStatus() + ", it can no longer be changed.");
        }
        // The links are fixed when the case is opened.
        if (!Objects.equals(diagnosticCase.getCustomerId(), request.customerId())
                || !Objects.equals(diagnosticCase.getApplianceId(), request.applianceId())
                || !Objects.equals(diagnosticCase.getServiceAddressId(), request.serviceAddressId())
                || !Objects.equals(diagnosticCase.getRelatedCaseId(), request.relatedCaseId())) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD,
                    "The customer, appliance, address and related case of a case cannot be changed.");
        }

        String reportedSymptoms = request.reportedSymptoms().trim();
        PrimarySymptom primarySymptom = request.primarySymptom() == null ? diagnosticCase.getPrimarySymptom() : request.primarySymptom();
        // Compare the JSON as values, because PostgreSQL stores its own spacing of the text.
        JsonNode oldSecondary = jsonFieldSupport.read(diagnosticCase.getSecondarySymptoms(), true);
        JsonNode oldTiming = jsonFieldSupport.read(diagnosticCase.getFailureTiming(), false);
        JsonNode newSecondary = secondarySymptoms == null ? oldSecondary : jsonFieldSupport.read(secondarySymptoms, true);
        JsonNode newTiming = failureTiming == null ? oldTiming : jsonFieldSupport.read(failureTiming, false);

        // Compare before applying the change. Only the field names go to the audit trail.
        ChangedFields changedFields = new ChangedFields()
                .track("reportedSymptoms", diagnosticCase.getReportedSymptoms(), reportedSymptoms)
                .track("primarySymptom", diagnosticCase.getPrimarySymptom(), primarySymptom)
                .track("secondarySymptoms", oldSecondary, newSecondary)
                .track("failureTiming", oldTiming, newTiming);

        diagnosticCase.setReportedSymptoms(reportedSymptoms);
        diagnosticCase.setPrimarySymptom(primarySymptom);
        if (secondarySymptoms != null) {
            diagnosticCase.setSecondarySymptoms(secondarySymptoms);
        }
        if (failureTiming != null) {
            diagnosticCase.setFailureTiming(failureTiming);
        }
        // Flush so that the row_version is the new one when the response is built.
        diagnosticCaseRepository.flush();
        auditService.recordInTransaction(AuditAction.UPDATE, AUDIT_ENTITY_TYPE, diagnosticCaseId,
                Map.of("changedFields", changedFields.names()));
        return diagnosticCase.getRowVersion();
    }

    @Transactional
    public Long transition(UUID actorUserId, UUID diagnosticCaseId, Long expectedRowVersion, CaseTransitionRequest request, Instant clientRecordedAt) {
        // Load the case and check the client saw the current version.
        DiagnosticCase diagnosticCase = diagnosticCaseRepository.findById(diagnosticCaseId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.NOT_FOUND,
                        "The current diagnostic case: " + diagnosticCaseId + " not found."));

        // The client must have seen the current version; otherwise the other device wins and this one re-reads.
        if (!diagnosticCase.getRowVersion().equals(expectedRowVersion)) {
            throw new ApplicationException(ErrorCode.VERSION_CONFLICT,
                    "The case was changed by someone else.", false, diagnosticCase.getRowVersion());
        }

        WorkflowStatus from = diagnosticCase.getWorkflowStatus();
        WorkflowStatus to = request.toState();
        String reason = blankToNull(request.reason());

        // Only needed when resuming from ON_HOLD.
        WorkflowStatus statusBeforeHold = null;
        if (from == WorkflowStatus.ON_HOLD) {
            String before = stateEventService.stateBeforeHold(
                    StateEventEntityType.CASE, diagnosticCaseId, WorkflowStatus.ON_HOLD.name());
            statusBeforeHold = before == null ? null : WorkflowStatus.valueOf(before);
        }

        CaseStateMachine.validate(from, to, reason, request.disposition(), statusBeforeHold);

        // Validation passed: now it is safe to change the entity.
        if (from == WorkflowStatus.ON_HOLD) {
            diagnosticCase.setHoldReason(null);
        }
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
        stateEventService.record(StateEventEntityType.CASE, diagnosticCaseId, from.name(), to.name(), reason, actorUserId, clientRecordedAt);

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
                    changeRow.getPrimarySymptom() == null ? null : PrimarySymptom.valueOf(changeRow.getPrimarySymptom()),
                    jsonFieldSupport.read(changeRow.getSecondarySymptoms(), true),
                    jsonFieldSupport.read(changeRow.getFailureTiming(), false),
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
                new ApplicationException(ErrorCode.INVALID_PAYLOAD, "The appliance: " + applianceId + " does not exist."));
        if (!appliance.getCustomerId().equals(customerId)) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD, "The appliance does not belong to this customer.");
        }
        ServiceAddress serviceAddress = serviceAddressRepository.findById(serviceAddressId).orElse(null);
        if (Objects.isNull(serviceAddress)) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD, "The service address: " + serviceAddressId + " does not exist.");
        }

        if (!serviceAddress.getCustomerId().equals(customerId)) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD, "The service address: " + serviceAddress.getAddressLine1() + " is not the current customer's address");
        }

        if (relatedCaseId != null && (relatedCaseId.equals(diagnosticCaseId) || !diagnosticCaseRepository.existsById(relatedCaseId))) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD, "The related case: " + relatedCaseId + " does not exist.");
        }
    }
}
