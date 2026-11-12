package com.hugo.huds.invoice.service;

import com.hugo.huds.audit.dto.AuditAction;
import com.hugo.huds.audit.service.AuditService;
import com.hugo.huds.audit.service.ChangedFields;
import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.diagnosticcase.repository.DiagnosticCaseRepository;
import com.hugo.huds.invoice.dto.CaseInvoiceDetailResponse;
import com.hugo.huds.invoice.dto.CaseInvoiceRequest;
import com.hugo.huds.invoice.dto.InvoiceStatus;
import com.hugo.huds.invoice.entity.CaseInvoice;
import com.hugo.huds.invoice.repository.CaseInvoiceRepository;
import com.hugo.huds.sync.dto.SyncPullResponse;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Business operations for the bill of a case. Amounts are in CAD.
 *
 * <p>There is one bill per case. The fees can be changed while the bill is a draft; once it is issued or void
 * only the status can still move on (ISSUED -> VOID). The subtotal, tax and total are worked out by the database.</p>
 */
@Validated
@Service
@Transactional(readOnly = true)
public class CaseInvoiceService {

    private static final String AUDIT_ENTITY_TYPE = "CASE_INVOICE";

    private final CaseInvoiceRepository caseInvoiceRepository;
    private final DiagnosticCaseRepository diagnosticCaseRepository;
    private final AuditService auditService;

    public CaseInvoiceService(CaseInvoiceRepository caseInvoiceRepository,
                              DiagnosticCaseRepository diagnosticCaseRepository,
                              AuditService auditService) {
        this.caseInvoiceRepository = caseInvoiceRepository;
        this.diagnosticCaseRepository = diagnosticCaseRepository;
        this.auditService = auditService;
    }

    /**
     * Adds the bill of a case, with the id chosen by the caller (the client generates it offline).
     * A new bill is always a draft.
     *
     * @param caseInvoiceId the id of the new bill
     * @param request       the bill details
     * @return the row version of the new row
     */
    @Transactional
    public Long add(@NotNull UUID caseInvoiceId, @NotNull CaseInvoiceRequest request) {
        // A retried request with the same id: the row already exists, so return it as is.
        Optional<CaseInvoice> existing = caseInvoiceRepository.findById(caseInvoiceId);
        if (existing.isPresent()) {
            return existing.get().getRowVersion();
        }

        if (request.invoiceStatus() != null && !InvoiceStatus.creatableStatuses().contains(request.invoiceStatus())) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD, "A new bill must be a draft.");
        }
        requireCase(request.diagnosticCaseId());

        // One bill per case.
        Optional<CaseInvoice> sameCase = caseInvoiceRepository.findByDiagnosticCaseId(request.diagnosticCaseId());
        if (sameCase.isPresent()) {
            throw ApplicationException.duplicate("The case already has a bill.", sameCase.get().getCaseInvoiceId());
        }

        CaseInvoice invoice = new CaseInvoice();
        invoice.setCaseInvoiceId(caseInvoiceId);
        invoice.setDiagnosticCaseId(request.diagnosticCaseId());
        invoice.setServiceCallFee(orZero(request.serviceCallFee()));
        invoice.setLabourFee(orZero(request.labourFee()));
        invoice.setPartsFee(orZero(request.partsFee()));
        invoice.setDiscountAmount(orZero(request.discountAmount()));
        invoice.setTaxRate(orZero(request.taxRate()));
        requireDiscountWithinSubtotal(invoice);
        // saveAndFlush so that the row_version and audit timestamps are populated before they are read.
        caseInvoiceRepository.saveAndFlush(invoice);
        auditService.recordInTransaction(AuditAction.CREATE, AUDIT_ENTITY_TYPE, caseInvoiceId, Map.of());
        return invoice.getRowVersion();
    }

    /**
     * Changes the bill of a case, provided the caller has seen the current version.
     * The case of a bill never changes. A missing amount, rate or status means "unchanged".
     *
     * @param caseInvoiceId the id of the bill
     * @param rowVersion    the version on which the caller based the change
     * @param request       the new values
     * @return the new row version
     */
    @Transactional
    public Long update(@NotNull UUID caseInvoiceId, @NotNull Long rowVersion, @NotNull CaseInvoiceRequest request) {
        CaseInvoice invoice = caseInvoiceRepository.findById(caseInvoiceId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.NOT_FOUND,
                        "Case invoice " + caseInvoiceId + " not found."));
        if (!Objects.equals(invoice.getRowVersion(), rowVersion)) {
            throw new ApplicationException(ErrorCode.VERSION_CONFLICT,
                    "The bill was changed by someone else.", false, invoice.getRowVersion());
        }
        if (!Objects.equals(invoice.getDiagnosticCaseId(), request.diagnosticCaseId())) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD, "The case of a bill cannot be changed.");
        }

        InvoiceStatus from = invoice.getInvoiceStatus();
        InvoiceStatus to = request.invoiceStatus() == null ? from : request.invoiceStatus();
        if (!from.canMoveTo(to)) {
            throw new ApplicationException(ErrorCode.INVALID_TRANSITION,
                    "A bill that is " + from + " cannot move to " + to + ".");
        }

        BigDecimal serviceCallFee = valueOrOld(request.serviceCallFee(), invoice.getServiceCallFee());
        BigDecimal labourFee = valueOrOld(request.labourFee(), invoice.getLabourFee());
        BigDecimal partsFee = valueOrOld(request.partsFee(), invoice.getPartsFee());
        BigDecimal discountAmount = valueOrOld(request.discountAmount(), invoice.getDiscountAmount());
        BigDecimal taxRate = valueOrOld(request.taxRate(), invoice.getTaxRate());

        boolean feesChanged = differs(serviceCallFee, invoice.getServiceCallFee())
                || differs(labourFee, invoice.getLabourFee())
                || differs(partsFee, invoice.getPartsFee())
                || differs(discountAmount, invoice.getDiscountAmount())
                || differs(taxRate, invoice.getTaxRate());
        // The fees of an issued or void bill are fixed.
        if (feesChanged && !from.isFeeEditable()) {
            throw new ApplicationException(ErrorCode.DOMAIN_RULE_VIOLATION,
                    "The bill is " + from + ", its fees can no longer be changed.");
        }

        // Issuing sets the time: the one sent by the device (it may have been offline), or now.
        Instant issuedAt = invoice.getIssuedAt();
        if (to == InvoiceStatus.ISSUED && from != InvoiceStatus.ISSUED) {
            issuedAt = request.issuedAt() == null ? Instant.now() : request.issuedAt();
        }

        // Compare before applying the change. Only the field names go to the audit trail.
        ChangedFields changedFields = new ChangedFields()
                .track("invoiceStatus", from, to)
                .track("issuedAt", invoice.getIssuedAt(), issuedAt)
                .track("serviceCallFee", invoice.getServiceCallFee().stripTrailingZeros(), serviceCallFee.stripTrailingZeros())
                .track("labourFee", invoice.getLabourFee().stripTrailingZeros(), labourFee.stripTrailingZeros())
                .track("partsFee", invoice.getPartsFee().stripTrailingZeros(), partsFee.stripTrailingZeros())
                .track("discountAmount", invoice.getDiscountAmount().stripTrailingZeros(), discountAmount.stripTrailingZeros())
                .track("taxRate", invoice.getTaxRate().stripTrailingZeros(), taxRate.stripTrailingZeros());

        invoice.setInvoiceStatus(to);
        invoice.setIssuedAt(issuedAt);
        invoice.setServiceCallFee(serviceCallFee);
        invoice.setLabourFee(labourFee);
        invoice.setPartsFee(partsFee);
        invoice.setDiscountAmount(discountAmount);
        invoice.setTaxRate(taxRate);
        requireDiscountWithinSubtotal(invoice);
        // Flush so that the row_version is the new one when it is read.
        caseInvoiceRepository.flush();
        auditService.recordInTransaction(AuditAction.UPDATE, AUDIT_ENTITY_TYPE, caseInvoiceId,
                Map.of("changedFields", changedFields.names()));
        return invoice.getRowVersion();
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
        List<CaseInvoiceRepository.ChangeRow> rows = caseInvoiceRepository.findChanges(since, sinceId, limit + 1);
        // There is no change row, return default response
        if (CollectionUtils.isEmpty(rows)) {
            return SyncPullResponse.nullListResponse(since, sinceId);
        }

        boolean hasMore = rows.size() > limit;
        List<CaseInvoiceRepository.ChangeRow> page = hasMore ? rows.subList(0, limit) : rows;

        List<CaseInvoiceDetailResponse> responses = page.stream().map(row -> new CaseInvoiceDetailResponse(
                row.getCaseInvoiceId(),
                row.getDiagnosticCaseId(),
                row.getInvoiceStatus() == null ? null : InvoiceStatus.valueOf(row.getInvoiceStatus()),
                row.getIssuedAt(),
                row.getServiceCallFee(),
                row.getLabourFee(),
                row.getPartsFee(),
                row.getDiscountAmount(),
                row.getTaxRate(),
                row.getSubtotalAmount(),
                row.getTaxAmount(),
                row.getTotalAmount(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                row.getCreator(),
                row.getEditor(),
                row.getRowVersion())).toList();

        CaseInvoiceRepository.ChangeRow last = page.get(page.size() - 1);
        return new SyncPullResponse(responses, last.getUpdatedAt(), last.getCaseInvoiceId(), hasMore);
    }

    /** The case must exist. A bill can be made and settled after the case is closed, so its status does not matter. */
    private void requireCase(UUID diagnosticCaseId) {
        if (!diagnosticCaseRepository.existsById(diagnosticCaseId)) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD,
                    "The diagnostic case: " + diagnosticCaseId + " does not exist.");
        }
    }

    /** The database checks this too; this gives a clear message instead of a constraint error. */
    private void requireDiscountWithinSubtotal(CaseInvoice invoice) {
        if (invoice.getDiscountAmount().compareTo(invoice.subtotal()) > 0) {
            throw new ApplicationException(ErrorCode.DOMAIN_RULE_VIOLATION,
                    "The discount cannot be more than the subtotal.");
        }
    }

    private BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal valueOrOld(BigDecimal value, BigDecimal old) {
        return value == null ? old : value;
    }

    /** Amounts are compared by value, so 10.0 and 10.00 are the same. */
    private boolean differs(BigDecimal left, BigDecimal right) {
        return left.compareTo(right) != 0;
    }
}
