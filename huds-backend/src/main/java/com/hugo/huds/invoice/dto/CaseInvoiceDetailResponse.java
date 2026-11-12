package com.hugo.huds.invoice.dto;

import com.hugo.huds.user.reponse.UserBriefResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The bill of one case as GET /api/v1/sync/pull returns it (entityType CASE_INVOICE).
 * The subtotal, tax and total are worked out by the database. A bill is never archived (it can be VOID).
 */
public record CaseInvoiceDetailResponse(UUID caseInvoiceId,
                                        UUID diagnosticCaseId,
                                        InvoiceStatus invoiceStatus,
                                        Instant issuedAt,
                                        BigDecimal serviceCallFee,
                                        BigDecimal labourFee,
                                        BigDecimal partsFee,
                                        BigDecimal discountAmount,
                                        BigDecimal taxRate,
                                        BigDecimal subtotalAmount,
                                        BigDecimal taxAmount,
                                        BigDecimal totalAmount,
                                        Instant createdAt,
                                        Instant updatedAt,
                                        UserBriefResponse creator,
                                        UserBriefResponse editor,
                                        Long rowVersion) {
}
