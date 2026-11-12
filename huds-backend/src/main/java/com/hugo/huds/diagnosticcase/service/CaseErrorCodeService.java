package com.hugo.huds.diagnosticcase.service;

import com.hugo.huds.audit.dto.AuditAction;
import com.hugo.huds.audit.service.AuditService;
import com.hugo.huds.audit.service.ChangedFields;
import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.diagnosticcase.dto.CaseErrorCodeDetailResponse;
import com.hugo.huds.diagnosticcase.dto.CaseErrorCodeRequest;
import com.hugo.huds.diagnosticcase.dto.ErrorCodeStatus;
import com.hugo.huds.diagnosticcase.dto.WorkflowStatus;
import com.hugo.huds.diagnosticcase.entity.CaseErrorCode;
import com.hugo.huds.diagnosticcase.entity.DiagnosticCase;
import com.hugo.huds.diagnosticcase.repository.CaseErrorCodeRepository;
import com.hugo.huds.diagnosticcase.repository.DiagnosticCaseRepository;
import com.hugo.huds.sync.dto.SyncPullResponse;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static com.hugo.huds.common.util.ParamNormalize.blankToNull;

/**
 * Business operations for the error codes of a case (ERROR CODE / DISPLAY MESSAGE of the diagnostic form).
 */
@Validated
@Service
@Transactional(readOnly = true)
public class CaseErrorCodeService {

    private static final String AUDIT_ENTITY_TYPE = "CASE_ERROR_CODE";

    private final CaseErrorCodeRepository caseErrorCodeRepository;
    private final DiagnosticCaseRepository diagnosticCaseRepository;
    private final AuditService auditService;

    public CaseErrorCodeService(CaseErrorCodeRepository caseErrorCodeRepository,
                                DiagnosticCaseRepository diagnosticCaseRepository,
                                AuditService auditService) {
        this.caseErrorCodeRepository = caseErrorCodeRepository;
        this.diagnosticCaseRepository = diagnosticCaseRepository;
        this.auditService = auditService;
    }

    /**
     * Adds an error code to an open case, with the id chosen by the caller (the client generates it offline).
     *
     * @param caseErrorCodeId the id of the new error code
     * @param request         the error code details
     * @return the row version of the new row
     */
    @Transactional
    public Long add(@NotNull UUID caseErrorCodeId, @NotNull CaseErrorCodeRequest request) {
        // A retried request with the same id: the row already exists, so return it as is.
        Optional<CaseErrorCode> existing = caseErrorCodeRepository.findById(caseErrorCodeId);
        if (existing.isPresent()) {
            return existing.get().getRowVersion();
        }

        requireOpenCase(request.diagnosticCaseId());

        String errorCode = request.errorCode().trim();
        ErrorCodeStatus codeStatus = request.codeStatus() == null ? ErrorCodeStatus.ACTIVE : request.codeStatus();
        throwIfDuplicate(request.diagnosticCaseId(), errorCode, codeStatus, null);

        CaseErrorCode caseErrorCode = new CaseErrorCode();
        caseErrorCode.setCaseErrorCodeId(caseErrorCodeId);
        caseErrorCode.setDiagnosticCaseId(request.diagnosticCaseId());
        caseErrorCode.setErrorCode(errorCode);
        caseErrorCode.setDisplayMessage(blankToNull(request.displayMessage()));
        caseErrorCode.setCodeStatus(codeStatus);
        // saveAndFlush so that the row_version and audit timestamps are populated before they are read.
        caseErrorCodeRepository.saveAndFlush(caseErrorCode);
        auditService.recordInTransaction(AuditAction.CREATE, AUDIT_ENTITY_TYPE, caseErrorCodeId, Map.of());
        return caseErrorCode.getRowVersion();
    }

    /**
     * Changes an error code of an open case, provided the caller has seen the current version.
     * The case of an error code never changes. An empty status in the request means "unchanged".
     *
     * @param caseErrorCodeId the id of the error code
     * @param rowVersion      the version on which the caller based the change
     * @param request         the new values
     * @return the new row version
     */
    @Transactional
    public Long update(@NotNull UUID caseErrorCodeId, @NotNull Long rowVersion, @NotNull CaseErrorCodeRequest request) {
        CaseErrorCode caseErrorCode = caseErrorCodeRepository.findById(caseErrorCodeId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.NOT_FOUND,
                        "Case error code " + caseErrorCodeId + " not found."));
        if (!Objects.equals(caseErrorCode.getRowVersion(), rowVersion)) {
            throw new ApplicationException(ErrorCode.VERSION_CONFLICT,
                    "The error code was changed by someone else.", false, caseErrorCode.getRowVersion());
        }
        if (!Objects.equals(caseErrorCode.getDiagnosticCaseId(), request.diagnosticCaseId())) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD, "The case of an error code cannot be changed.");
        }
        requireOpenCase(caseErrorCode.getDiagnosticCaseId());

        String errorCode = request.errorCode().trim();
        String displayMessage = blankToNull(request.displayMessage());
        ErrorCodeStatus codeStatus = request.codeStatus() == null ? caseErrorCode.getCodeStatus() : request.codeStatus();
        throwIfDuplicate(caseErrorCode.getDiagnosticCaseId(), errorCode, codeStatus, caseErrorCodeId);

        // Compare before applying the change. Only the field names go to the audit trail.
        ChangedFields changedFields = new ChangedFields()
                .track("errorCode", caseErrorCode.getErrorCode(), errorCode)
                .track("displayMessage", caseErrorCode.getDisplayMessage(), displayMessage)
                .track("codeStatus", caseErrorCode.getCodeStatus(), codeStatus);

        caseErrorCode.setErrorCode(errorCode);
        caseErrorCode.setDisplayMessage(displayMessage);
        caseErrorCode.setCodeStatus(codeStatus);
        // Flush so that the row_version is the new one when it is read.
        caseErrorCodeRepository.flush();
        auditService.recordInTransaction(AuditAction.UPDATE, AUDIT_ENTITY_TYPE, caseErrorCodeId,
                Map.of("changedFields", changedFields.names()));
        return caseErrorCode.getRowVersion();
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
        List<CaseErrorCodeRepository.ChangeRow> rows = caseErrorCodeRepository.findChanges(since, sinceId, limit + 1);
        // There is no change row, return default response
        if (CollectionUtils.isEmpty(rows)) {
            return SyncPullResponse.nullListResponse(since, sinceId);
        }

        boolean hasMore = rows.size() > limit;
        List<CaseErrorCodeRepository.ChangeRow> page = hasMore ? rows.subList(0, limit) : rows;

        List<CaseErrorCodeDetailResponse> responses = page.stream().map(row -> new CaseErrorCodeDetailResponse(
                row.getCaseErrorCodeId(),
                row.getDiagnosticCaseId(),
                row.getErrorCode(),
                row.getDisplayMessage(),
                row.getCodeStatus() == null ? null : ErrorCodeStatus.valueOf(row.getCodeStatus()),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                row.getCreator(),
                row.getEditor(),
                row.getRowVersion())).toList();

        CaseErrorCodeRepository.ChangeRow last = page.get(page.size() - 1);
        return new SyncPullResponse(responses, last.getUpdatedAt(), last.getCaseErrorCodeId(), hasMore);
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
                    "The case is " + diagnosticCase.getWorkflowStatus() + ", its error codes can no longer be changed.");
        }
    }

    /**
     * The same code (ignoring case) is recorded once per case and status.
     *
     * @param excludeId the error code being updated, or null when adding
     */
    private void throwIfDuplicate(UUID diagnosticCaseId, String errorCode, ErrorCodeStatus codeStatus, UUID excludeId) {
        Optional<CaseErrorCode> duplicate = caseErrorCodeRepository.findDuplicate(diagnosticCaseId, errorCode, codeStatus);
        if (duplicate.isPresent() && !duplicate.get().getCaseErrorCodeId().equals(excludeId)) {
            throw ApplicationException.duplicate(
                    "The case already has this error code with the same status.",
                    duplicate.get().getCaseErrorCodeId());
        }
    }
}
