package com.hugo.huds.invoice.dto;

import com.hugo.huds.user.reponse.UserBriefResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One payment as GET /api/v1/sync/pull returns it (entityType CASE_INVOICE_PAYMENT).
 * A payment is never archived, so there is no "archived" flag.
 */
public record CaseInvoicePaymentDetailResponse(UUID caseInvoicePaymentId,
                                               UUID caseInvoiceId,
                                               BigDecimal amount,
                                               PaymentMethod paymentMethod,
                                               String referenceNo,
                                               Instant receivedAt,
                                               Instant createdAt,
                                               Instant updatedAt,
                                               UserBriefResponse creator,
                                               UserBriefResponse editor,
                                               Long rowVersion) {
}
