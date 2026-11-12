package com.hugo.huds.invoice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Body of a CASE_INVOICE_PAYMENT CREATE or UPDATE mutation: one payment received for a bill. Amounts are in CAD.
 * The bill of a payment never changes, so on UPDATE it must match the stored row. Every other field is replaced
 * as sent.
 */
public record CaseInvoicePaymentRequest(
        @NotNull(message = "CaseInvoiceId is required")
        UUID caseInvoiceId,
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
        @Digits(integer = 10, fraction = 2, message = "Amount has too many digits")
        BigDecimal amount,
        @NotNull(message = "PaymentMethod is required")
        PaymentMethod paymentMethod,
        /* For example a cheque number or an e-transfer reference. */
        @Size(max = 100, message = "ReferenceNo is too long")
        String referenceNo,
        @NotNull(message = "ReceivedAt is required")
        Instant receivedAt
) {
}
