package com.hugo.huds.diagnosticcase.casepart.service;

import com.hugo.huds.audit.dto.AuditAction;
import com.hugo.huds.audit.service.AuditService;
import com.hugo.huds.audit.service.ChangedFields;
import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.diagnosticcase.casepart.dto.CasePartDetailResponse;
import com.hugo.huds.diagnosticcase.casepart.dto.CasePartRequest;
import com.hugo.huds.diagnosticcase.casepart.dto.PartsSupplyStatus;
import com.hugo.huds.diagnosticcase.casepart.entity.CasePart;
import com.hugo.huds.diagnosticcase.casepart.repository.CasePartRepository;
import com.hugo.huds.sync.dto.SyncPullResponse;
import com.hugo.huds.user.reponse.UserBriefResponse;
import com.hugo.huds.user.service.UserService;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.hugo.huds.common.util.ParamNormalize.blankToNull;

/**
 * The parts a case needs: what is to be waited for, when it arrives, and how long it is under warranty.
 * Every change comes in through the sync push, and the other devices get it through the sync pull.
 */
@Validated
@Service
@Slf4j
@Transactional(readOnly = true)
public class CasePartService {

    private final CasePartRepository casePartRepository;
    private final AuditService auditService;
    private final UserService userService;
    // Do not use the SyncEntityType.CASE_PART, it would lead to circular dependencies.
    private static final String AUDIT_ENTITY_TYPE = "CASE_PART";

    public CasePartService(CasePartRepository casePartRepository, AuditService auditService, UserService userService) {
        this.casePartRepository = casePartRepository;
        this.auditService = auditService;
        this.userService = userService;
    }

    /**
     * Creates a part with the id chosen by the caller (the client generated it offline).
     *
     * @param casePartId the id of the part
     * @param request    the part details
     * @return the row version of the new part
     */
    @Transactional
    public Long add(@NotNull UUID casePartId, @NotNull CasePartRequest request) {

        // A retried request with the same id: the part already exists, so return it as is.
        var existing = casePartRepository.findById(casePartId);
        if (existing.isPresent()) {
            return existing.get().getRowVersion();
        }

        checkRules(request);

        CasePart part = new CasePart();
        part.setCasePartId(casePartId);
        part.setDiagnosticCaseId(request.diagnosticCaseId());
        apply(part, request);
        // saveAndFlush so that the row_version is populated before it is returned.
        casePartRepository.saveAndFlush(part);
        auditService.recordInTransaction(AuditAction.CREATE, AUDIT_ENTITY_TYPE, casePartId, Map.of());
        return part.getRowVersion();
    }

    /**
     * Updates a part, provided the caller has seen the current version. The case of a part cannot change.
     *
     * @param casePartId the id of the part
     * @param rowVersion the version on which the caller based the change
     * @param request    the new part details
     * @return the new row version
     */
    @Transactional
    public Long update(@NotNull UUID casePartId, @NotNull Long rowVersion, @NotNull CasePartRequest request) {

        CasePart part = findActive(casePartId);
        checkVersion(part, rowVersion);
        if (!Objects.equals(part.getDiagnosticCaseId(), request.diagnosticCaseId())) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD, "The case of a part cannot be changed.");
        }
        checkRules(request);

        // Compare before applying the change. Only the field names go to the audit trail.
        ChangedFields changes = new ChangedFields()
                .track("partName", part.getPartName(), request.partName().trim())
                .track("oemPartNo", part.getOemPartNo(), blankToNull(request.oemPartNo()))
                .track("quantity", part.getQuantity(), request.quantity())
                .track("unit", part.getUnit(), blankToNull(request.unit()))
                .track("supplier", part.getSupplier(), blankToNull(request.supplier()))
                .track("partsSupplyStatus", part.getPartsSupplyStatus(), request.partsSupplyStatus())
                .track("orderedOn", part.getOrderedOn(), request.orderedOn())
                .track("expectedArrivalOn", part.getExpectedArrivalOn(), request.expectedArrivalOn())
                .track("arrivedOn", part.getArrivedOn(), request.arrivedOn())
                .track("warrantyMonths", part.getWarrantyMonths(), request.warrantyMonths())
                .track("installedVisitId", part.getInstalledVisitId(), request.installedVisitId());

        apply(part, request);
        // Flush so that the row_version is new when it is returned.
        casePartRepository.flush();
        auditService.recordInTransaction(AuditAction.UPDATE, AUDIT_ENTITY_TYPE, casePartId, Map.of("changedFields", changes.names()));
        return part.getRowVersion();
    }

    /**
     * Soft-deletes a part (for example one that was entered by mistake). Archived parts are invisible to every
     * entity query (see @SQLRestriction at CasePart), so a second call for the same id reports "not found".
     *
     * @param casePartId the id of the part to archive
     * @param rowVersion the version on which the caller based the change
     * @return the new row version
     */
    @Transactional
    public Long archive(@NotNull UUID casePartId, @NotNull Long rowVersion) {
        CasePart part = findActive(casePartId);
        checkVersion(part, rowVersion);

        part.archive(Instant.now());
        casePartRepository.flush();
        auditService.recordInTransaction(AuditAction.ARCHIVE, AUDIT_ENTITY_TYPE, casePartId, Map.of());
        return part.getRowVersion();
    }

    /**
     * Whether the case still has a part that has to be waited for (ordered or on backorder).
     * The case service calls this before it lets a case leave PENDING_PARTS.
     *
     * @param diagnosticCaseId the id of the case
     * @return true if at least one part has not arrived yet
     */
    public boolean hasWaitingParts(@NotNull UUID diagnosticCaseId) {
        return casePartRepository.existsByDiagnosticCaseIdAndPartsSupplyStatusIn(
                diagnosticCaseId, List.of(PartsSupplyStatus.ORDERED, PartsSupplyStatus.BACKORDER));
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

        List<CasePartRepository.ChangeRow> rows = casePartRepository.findChanges(since, sinceId, limit + 1);
        // There is no change row, return default response
        if (CollectionUtils.isEmpty(rows)) {
            return SyncPullResponse.nullListResponse(since, sinceId);
        }

        boolean hasMore = rows.size() > limit;
        List<CasePartRepository.ChangeRow> page = hasMore ? rows.subList(0, limit) : rows;

        // One lookup for all the user names of the page, instead of a join in the query.
        Set<UUID> userIds = new HashSet<>();
        for (CasePartRepository.ChangeRow row : page) {
            if (row.getCreatedBy() != null) {
                userIds.add(row.getCreatedBy());
            }
            if (row.getUpdatedBy() != null) {
                userIds.add(row.getUpdatedBy());
            }
        }
        Map<UUID, UserBriefResponse> users = userService.findByUserIds(userIds);

        List<CasePartDetailResponse> items = page.stream().map(row -> new CasePartDetailResponse(
                row.getCasePartId(),
                row.getDiagnosticCaseId(),
                row.getPartName(),
                row.getOemPartNo(),
                row.getQuantity(),
                row.getUnit(),
                row.getSupplier(),
                PartsSupplyStatus.valueOf(row.getPartsSupplyStatus()),
                row.getOrderedOn(),
                row.getExpectedArrivalOn(),
                row.getArrivedOn(),
                row.getWarrantyMonths(),
                row.getInstalledVisitId(),
                row.getRowVersion(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                users.get(row.getCreatedBy()),
                users.get(row.getUpdatedBy()),
                Boolean.TRUE.equals(row.getArchived()))).toList();

        CasePartRepository.ChangeRow last = page.get(page.size() - 1);
        return new SyncPullResponse(items, last.getUpdatedAt(), last.getCasePartId(), hasMore);
    }

    /** Copies the request into the part. The case and the id are set by the caller, once, when the part is created. */
    private void apply(CasePart part, CasePartRequest request) {
        part.setPartName(request.partName().trim());
        part.setOemPartNo(blankToNull(request.oemPartNo()));
        part.setQuantity(request.quantity());
        part.setUnit(blankToNull(request.unit()));
        part.setSupplier(blankToNull(request.supplier()));
        part.setPartsSupplyStatus(request.partsSupplyStatus());
        part.setOrderedOn(request.orderedOn());
        part.setExpectedArrivalOn(request.expectedArrivalOn());
        part.setArrivedOn(request.arrivedOn());
        part.setWarrantyMonths(request.warrantyMonths());
        part.setInstalledVisitId(request.installedVisitId());
    }

    /** Rules between the fields that the field annotations of the request cannot express. */
    private void checkRules(CasePartRequest request) {
        if (request.partsSupplyStatus() == PartsSupplyStatus.ARRIVED && request.arrivedOn() == null) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD, "The arrival date is required for a part that has arrived.");
        }
        if (request.orderedOn() != null && request.expectedArrivalOn() != null
                && request.expectedArrivalOn().isBefore(request.orderedOn())) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD, "The expected arrival cannot be before the order date.");
        }
    }

    private CasePart findActive(@NotNull UUID casePartId) {
        return casePartRepository.findById(casePartId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.NOT_FOUND, "Part " + casePartId + " not found."));
    }

    // Optimistic concurrency: the caller must have seen the current version
    private void checkVersion(@NotNull CasePart part, @NotNull Long rowVersion) {
        if (!Objects.equals(part.getRowVersion(), rowVersion)) {
            throw new ApplicationException(ErrorCode.VERSION_CONFLICT, "The part has already been modified by someone else.", false, part.getRowVersion());
        }
    }
}
