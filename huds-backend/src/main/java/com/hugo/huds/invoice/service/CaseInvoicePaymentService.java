package com.hugo.huds.invoice.service;

import com.hugo.huds.audit.dto.AuditAction;
import com.hugo.huds.audit.service.AuditService;
import com.hugo.huds.audit.service.ChangedFields;
import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.invoice.dto.CaseInvoicePaymentDetailResponse;
import com.hugo.huds.invoice.dto.CaseInvoicePaymentRequest;
import com.hugo.huds.invoice.dto.InvoiceStatus;
import com.hugo.huds.invoice.dto.PaymentMethod;
import com.hugo.huds.invoice.entity.CaseInvoice;
import com.hugo.huds.invoice.entity.CaseInvoicePayment;
import com.hugo.huds.invoice.repository.CaseInvoicePaymentRepository;
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

import static com.hugo.huds.common.util.ParamNormalize.blankToNull;

/**
 * Business operations for the payments of a bill. Amounts are in CAD.
 *
 * <p>A payment can be added or corrected while its bill is not void. A payment is never deleted: a wrong one is
 * corrected by editing it.</p>
 */
@Validated
@Service
@Transactional(readOnly = true)
public class CaseInvoicePaymentService {

    private static final String AUDIT_ENTITY_TYPE = "CASE_INVOICE_PAYMENT";

    private final CaseInvoicePaymentRepository caseInvoicePaymentRepository;
    private final CaseInvoiceRepository caseInvoiceRepository;
    private final AuditService auditService;

    public CaseInvoicePaymentService(CaseInvoicePaymentRepository caseInvoicePaymentRepository,
                                     CaseInvoiceRepository caseInvoiceRepository,
                                     AuditService auditService) {
        this.caseInvoicePaymentRepository = caseInvoicePaymentRepository;
        this.caseInvoiceRepository = caseInvoiceRepository;
        this.auditService = auditService;
    }

    /**
     * Adds a payment to a bill, with the id chosen by the caller (the client generates it offline).
     *
     * @param caseInvoicePaymentId the id of the new payment
     * @param request              the payment details
     * @return the row version of the new row
     */
    @Transactional
    public Long add(@NotNull UUID caseInvoicePaymentId, @NotNull CaseInvoicePaymentRequest request) {
        // A retried request with the same id: the row already exists, so return it as is.
        Optional<CaseInvoicePayment> existing = caseInvoicePaymentRepository.findById(caseInvoicePaymentId);
        if (existing.isPresent()) {
            return existing.get().getRowVersion();
        }

        requireOpenInvoice(request.caseInvoiceId());

        CaseInvoicePayment payment = new CaseInvoicePayment();
        payment.setCaseInvoicePaymentId(caseInvoicePaymentId);
        payment.setCaseInvoiceId(request.caseInvoiceId());
        payment.setAmount(request.amount());
        payment.setPaymentMethod(request.paymentMethod());
        payment.setReferenceNo(blankToNull(request.referenceNo()));
        payment.setReceivedAt(request.receivedAt());
        // saveAndFlush so that the row_version and audit timestamps are populated before they are read.
        caseInvoicePaymentRepository.saveAndFlush(payment);
        auditService.recordInTransaction(AuditAction.CREATE, AUDIT_ENTITY_TYPE, caseInvoicePaymentId, Map.of());
        return payment.getRowVersion();
    }

    /**
     * Changes a payment of a bill that is not void, provided the caller has seen the current version.
     * The bill of a payment never changes. Every other field is replaced as sent.
     *
     * @param caseInvoicePaymentId the id of the payment
     * @param rowVersion           the version on which the caller based the change
     * @param request              the new values
     * @return the new row version
     */
    @Transactional
    public Long update(@NotNull UUID caseInvoicePaymentId, @NotNull Long rowVersion,
                       @NotNull CaseInvoicePaymentRequest request) {
        CaseInvoicePayment payment = caseInvoicePaymentRepository.findById(caseInvoicePaymentId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.NOT_FOUND,
                        "Case invoice payment " + caseInvoicePaymentId + " not found."));
        if (!Objects.equals(payment.getRowVersion(), rowVersion)) {
            throw new ApplicationException(ErrorCode.VERSION_CONFLICT,
                    "The payment was changed by someone else.", false, payment.getRowVersion());
        }
        if (!Objects.equals(payment.getCaseInvoiceId(), request.caseInvoiceId())) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD, "The bill of a payment cannot be changed.");
        }
        requireOpenInvoice(payment.getCaseInvoiceId());

        BigDecimal amount = request.amount();
        PaymentMethod paymentMethod = request.paymentMethod();
        String referenceNo = blankToNull(request.referenceNo());
        Instant receivedAt = request.receivedAt();

        // Compare before applying the change. Only the field names go to the audit trail.
        ChangedFields changedFields = new ChangedFields()
                .track("amount", payment.getAmount().stripTrailingZeros(), amount.stripTrailingZeros())
                .track("paymentMethod", payment.getPaymentMethod(), paymentMethod)
                .track("referenceNo", payment.getReferenceNo(), referenceNo)
                .track("receivedAt", payment.getReceivedAt(), receivedAt);

        payment.setAmount(amount);
        payment.setPaymentMethod(paymentMethod);
        payment.setReferenceNo(referenceNo);
        payment.setReceivedAt(receivedAt);
        // Flush so that the row_version is the new one when it is read.
        caseInvoicePaymentRepository.flush();
        auditService.recordInTransaction(AuditAction.UPDATE, AUDIT_ENTITY_TYPE, caseInvoicePaymentId,
                Map.of("changedFields", changedFields.names()));
        return payment.getRowVersion();
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
        List<CaseInvoicePaymentRepository.ChangeRow> rows =
                caseInvoicePaymentRepository.findChanges(since, sinceId, limit + 1);
        // There is no change row, return default response
        if (CollectionUtils.isEmpty(rows)) {
            return SyncPullResponse.nullListResponse(since, sinceId);
        }

        boolean hasMore = rows.size() > limit;
        List<CaseInvoicePaymentRepository.ChangeRow> page = hasMore ? rows.subList(0, limit) : rows;

        List<CaseInvoicePaymentDetailResponse> responses = page.stream().map(row -> new CaseInvoicePaymentDetailResponse(
                row.getCaseInvoicePaymentId(),
                row.getCaseInvoiceId(),
                row.getAmount(),
                row.getPaymentMethod() == null ? null : PaymentMethod.valueOf(row.getPaymentMethod()),
                row.getReferenceNo(),
                row.getReceivedAt(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                row.getCreator(),
                row.getEditor(),
                row.getRowVersion())).toList();

        CaseInvoicePaymentRepository.ChangeRow last = page.get(page.size() - 1);
        return new SyncPullResponse(responses, last.getUpdatedAt(), last.getCaseInvoicePaymentId(), hasMore);
    }

    /** The bill must exist and must not be void; money cannot be recorded against a void bill. */
    private void requireOpenInvoice(UUID caseInvoiceId) {
        CaseInvoice invoice = caseInvoiceRepository.findById(caseInvoiceId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.INVALID_PAYLOAD,
                        "The bill: " + caseInvoiceId + " does not exist."));
        if (invoice.getInvoiceStatus() == InvoiceStatus.VOID) {
            throw new ApplicationException(ErrorCode.DOMAIN_RULE_VIOLATION,
                    "The bill is VOID, its payments can no longer be changed.");
        }
    }
}
