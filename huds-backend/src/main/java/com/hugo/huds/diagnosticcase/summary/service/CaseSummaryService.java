package com.hugo.huds.diagnosticcase.summary.service;

import com.hugo.huds.audit.dto.AuditAction;
import com.hugo.huds.audit.service.AuditService;
import com.hugo.huds.audit.service.ChangedFields;
import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.common.util.JsonFieldSupport;
import com.hugo.huds.diagnosticcase.dto.WorkflowStatus;
import com.hugo.huds.diagnosticcase.entity.DiagnosticCase;
import com.hugo.huds.diagnosticcase.repository.DiagnosticCaseRepository;
import com.hugo.huds.diagnosticcase.summary.dto.CaseSummaryDetailResponse;
import com.hugo.huds.diagnosticcase.summary.dto.CaseSummaryRequest;
import com.hugo.huds.diagnosticcase.summary.dto.CustomerDecision;
import com.hugo.huds.diagnosticcase.summary.dto.DiagnosticOutcome;
import com.hugo.huds.diagnosticcase.summary.dto.FailureCategory;
import com.hugo.huds.diagnosticcase.summary.dto.RepairStatus;
import com.hugo.huds.diagnosticcase.summary.entity.CaseSummaryRecord;
import com.hugo.huds.diagnosticcase.summary.repository.CaseSummaryRecordRepository;
import com.hugo.huds.sync.dto.SyncPullResponse;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.hugo.huds.common.util.ParamNormalize.blankToNull;

/**
 * Business operations for the summary of a case (sections 6 and 7 of the diagnostic record).
 *
 * <p>There is one summary per case. It can be written until the case is finished; a finished case is read only.
 * Who may write it is decided by the sync permission of the entity type.</p>
 */
@Validated
@Service
@Transactional(readOnly = true)
public class CaseSummaryService {

    private static final String AUDIT_ENTITY_TYPE = "CASE_SUMMARY";
    private static final Pattern CATEGORY_CODE = Pattern.compile("[A-Z_]+");

    private final CaseSummaryRecordRepository caseSummaryRecordRepository;
    private final DiagnosticCaseRepository diagnosticCaseRepository;
    private final AuditService auditService;
    private final JsonFieldSupport jsonFieldSupport;

    public CaseSummaryService(CaseSummaryRecordRepository caseSummaryRecordRepository,
                              DiagnosticCaseRepository diagnosticCaseRepository,
                              AuditService auditService,
                              JsonFieldSupport jsonFieldSupport) {
        this.caseSummaryRecordRepository = caseSummaryRecordRepository;
        this.diagnosticCaseRepository = diagnosticCaseRepository;
        this.auditService = auditService;
        this.jsonFieldSupport = jsonFieldSupport;
    }

    /**
     * Adds the summary of a case, with the id chosen by the caller (the client generates it offline).
     *
     * @param caseSummaryRecordId the id of the new summary
     * @param request             the summary details
     * @return the row version of the new row
     */
    @Transactional
    public Long add(@NotNull UUID caseSummaryRecordId, @NotNull CaseSummaryRequest request) {
        // A retried request with the same id: the row already exists, so return it as is.
        Optional<CaseSummaryRecord> existing = caseSummaryRecordRepository.findById(caseSummaryRecordId);
        if (existing.isPresent()) {
            return existing.get().getRowVersion();
        }

        requireOpenCase(request.diagnosticCaseId());

        // One summary per case.
        Optional<CaseSummaryRecord> sameCase = caseSummaryRecordRepository.findByDiagnosticCaseId(request.diagnosticCaseId());
        if (sameCase.isPresent()) {
            throw ApplicationException.duplicate("The case already has a summary.",
                    sameCase.get().getCaseSummaryRecordId());
        }

        Set<FailureCategory> categories = distinct(request.failureCategories());
        requirePrimaryInCategories(request.primaryFailureCategory(), categories);

        CaseSummaryRecord record = new CaseSummaryRecord();
        record.setCaseSummaryRecordId(caseSummaryRecordId);
        record.setDiagnosticCaseId(request.diagnosticCaseId());
        record.setDiagnosticOutcome(request.diagnosticOutcome());
        record.setRepairStatus(request.repairStatus());
        record.setCustomerDecision(request.customerDecision());
        record.setFailureCategories(toJsonArray(categories));
        record.setPrimaryFailureCategory(request.primaryFailureCategory());
        record.setDataTags(blankToNull(request.dataTags()));
        record.setTechnicianSummary(blankToNull(request.technicianSummary()));
        // saveAndFlush so that the row_version and audit timestamps are populated before they are read.
        caseSummaryRecordRepository.saveAndFlush(record);
        auditService.recordInTransaction(AuditAction.CREATE, AUDIT_ENTITY_TYPE, caseSummaryRecordId, Map.of());
        return record.getRowVersion();
    }

    /**
     * Changes the summary of an open case, provided the caller has seen the current version.
     * The case of a summary never changes. An empty category list means "unchanged"; every other field is
     * replaced as sent.
     *
     * @param caseSummaryRecordId the id of the summary
     * @param rowVersion          the version on which the caller based the change
     * @param request             the new values
     * @return the new row version
     */
    @Transactional
    public Long update(@NotNull UUID caseSummaryRecordId, @NotNull Long rowVersion, @NotNull CaseSummaryRequest request) {
        CaseSummaryRecord record = caseSummaryRecordRepository.findById(caseSummaryRecordId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.NOT_FOUND,
                        "Case summary " + caseSummaryRecordId + " not found."));
        if (!Objects.equals(record.getRowVersion(), rowVersion)) {
            throw new ApplicationException(ErrorCode.VERSION_CONFLICT,
                    "The summary was changed by someone else.", false, record.getRowVersion());
        }
        if (!Objects.equals(record.getDiagnosticCaseId(), request.diagnosticCaseId())) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD, "The case of a summary cannot be changed.");
        }
        requireOpenCase(record.getDiagnosticCaseId());

        // Missing means "unchanged"; the primary category is then checked against the stored categories.
        Set<FailureCategory> newCategories = distinct(request.failureCategories());
        boolean categoriesChanged = !newCategories.isEmpty();
        Set<FailureCategory> categories = categoriesChanged ? newCategories : storedCategories(record);
        requirePrimaryInCategories(request.primaryFailureCategory(), categories);

        String dataTags = blankToNull(request.dataTags());
        String technicianSummary = blankToNull(request.technicianSummary());
        String categoriesText = categoriesChanged ? toJsonArray(categories) : record.getFailureCategories();

        // Compare the JSON as values, because PostgreSQL stores its own spacing of the text.
        ChangedFields changedFields = new ChangedFields()
                .track("diagnosticOutcome", record.getDiagnosticOutcome(), request.diagnosticOutcome())
                .track("repairStatus", record.getRepairStatus(), request.repairStatus())
                .track("customerDecision", record.getCustomerDecision(), request.customerDecision())
                .track("failureCategories", storedCategories(record), categories)
                .track("primaryFailureCategory", record.getPrimaryFailureCategory(), request.primaryFailureCategory())
                .track("dataTags", record.getDataTags(), dataTags)
                .track("technicianSummary", record.getTechnicianSummary(), technicianSummary);

        record.setDiagnosticOutcome(request.diagnosticOutcome());
        record.setRepairStatus(request.repairStatus());
        record.setCustomerDecision(request.customerDecision());
        record.setFailureCategories(categoriesText);
        record.setPrimaryFailureCategory(request.primaryFailureCategory());
        record.setDataTags(dataTags);
        record.setTechnicianSummary(technicianSummary);
        // Flush so that the row_version is the new one when it is read.
        caseSummaryRecordRepository.flush();
        auditService.recordInTransaction(AuditAction.UPDATE, AUDIT_ENTITY_TYPE, caseSummaryRecordId,
                Map.of("changedFields", changedFields.names()));
        return record.getRowVersion();
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
        List<CaseSummaryRecordRepository.ChangeRow> rows = caseSummaryRecordRepository.findChanges(since, sinceId, limit + 1);
        // There is no change row, return default response
        if (CollectionUtils.isEmpty(rows)) {
            return SyncPullResponse.nullListResponse(since, sinceId);
        }

        boolean hasMore = rows.size() > limit;
        List<CaseSummaryRecordRepository.ChangeRow> page = hasMore ? rows.subList(0, limit) : rows;

        List<CaseSummaryDetailResponse> responses = page.stream().map(row -> new CaseSummaryDetailResponse(
                row.getCaseSummaryRecordId(),
                row.getDiagnosticCaseId(),
                row.getDiagnosticOutcome() == null ? null : DiagnosticOutcome.valueOf(row.getDiagnosticOutcome()),
                row.getRepairStatus() == null ? null : RepairStatus.valueOf(row.getRepairStatus()),
                row.getCustomerDecision() == null ? null : CustomerDecision.valueOf(row.getCustomerDecision()),
                jsonFieldSupport.read(row.getFailureCategories(), true),
                row.getPrimaryFailureCategory() == null ? null : FailureCategory.valueOf(row.getPrimaryFailureCategory()),
                row.getDataTags(),
                row.getTechnicianSummary(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                row.getCreator(),
                row.getEditor(),
                row.getRowVersion())).toList();

        CaseSummaryRecordRepository.ChangeRow last = page.get(page.size() - 1);
        return new SyncPullResponse(responses, last.getUpdatedAt(), last.getCaseSummaryRecordId(), hasMore);
    }

    /**
     * The case must exist and must not be finished; a finished case is read only.
     */
    private void requireOpenCase(UUID diagnosticCaseId) {
        DiagnosticCase diagnosticCase = diagnosticCaseRepository.findById(diagnosticCaseId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.INVALID_PAYLOAD,
                        "The diagnostic case: " + diagnosticCaseId + " does not exist."));
        if (WorkflowStatus.finishedStatuses().contains(diagnosticCase.getWorkflowStatus())) {
            throw new ApplicationException(ErrorCode.DOMAIN_RULE_VIOLATION,
                    "The case is " + diagnosticCase.getWorkflowStatus() + ", its summary can no longer be changed.");
        }
    }

    /** The primary category, when there is one, must be one of the categories (the database checks it too). */
    private void requirePrimaryInCategories(FailureCategory primary, Set<FailureCategory> categories) {
        if (primary != null && !categories.contains(primary)) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD,
                    "The primary failure category must be one of the failure categories.");
        }
    }

    /** The categories of the request without duplicates, in the order sent; empty when none were sent. */
    private Set<FailureCategory> distinct(List<FailureCategory> categories) {
        return categories == null ? new LinkedHashSet<>() : new LinkedHashSet<>(categories);
    }

    /** The categories stored in the row. The stored text is a compact array of codes, for example ["PMP","WIRE"]. */
    private Set<FailureCategory> storedCategories(CaseSummaryRecord record) {
        Set<FailureCategory> stored = new LinkedHashSet<>();
        Matcher matcher = CATEGORY_CODE.matcher(record.getFailureCategories() == null ? "" : record.getFailureCategories());
        while (matcher.find()) {
            stored.add(FailureCategory.valueOf(matcher.group()));
        }
        return stored;
    }

    /** The compact JSON array of the codes. The codes are enum names, so they need no escaping. */
    private String toJsonArray(Set<FailureCategory> categories) {
        return categories.stream().map(category -> "\"" + category.name() + "\"")
                .collect(Collectors.joining(",", "[", "]"));
    }
}
