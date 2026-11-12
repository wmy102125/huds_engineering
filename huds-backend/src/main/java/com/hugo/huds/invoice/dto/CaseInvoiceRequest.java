package com.hugo.huds.invoice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Body of a CASE_INVOICE CREATE or UPDATE mutation: the bill of a case. Amounts are in CAD.
 * The case of a bill never changes, so on UPDATE it must match the stored row.
 *
 * <p>A missing amount, rate or status means "0 / DRAFT" on create and "unchanged" on update.
 * The subtotal, tax and total are worked out by the database and are not part of the request.</p>
 */
public record CaseInvoiceRequest(
        @NotNull(message = "DiagnosticCaseId is required")
        UUID diagnosticCaseId,
        /* Empty means DRAFT on create and "unchanged" on update. */
        InvoiceStatus invoiceStatus,
        /* When the bill was issued. Empty on issuing means "now". Ignored unless the bill is being issued. */
        Instant issuedAt,
        /* Service Call & Diagnostic Fee: charged once per case, at the first visit. */
        @DecimalMin(value = "0", message = "ServiceCallFee cannot be negative")
        @Digits(integer = 10, fraction = 2, message = "ServiceCallFee has too many digits")
        BigDecimal serviceCallFee,
        /* Flat-Rate Labor / Service Performed. */
        @DecimalMin(value = "0", message = "LabourFee cannot be negative")
        @Digits(integer = 10, fraction = 2, message = "LabourFee has too many digits")
        BigDecimal labourFee,
        /* Specialized Replacement Parts & Factory Materials. */
        @DecimalMin(value = "0", message = "PartsFee cannot be negative")
        @Digits(integer = 10, fraction = 2, message = "PartsFee has too many digits")
        BigDecimal partsFee,
        @DecimalMin(value = "0", message = "DiscountAmount cannot be negative")
        @Digits(integer = 10, fraction = 2, message = "DiscountAmount has too many digits")
        BigDecimal discountAmount,
        /* 0 while HST is not charged. For example 0.1500 once HST applies. */
        @DecimalMin(value = "0", message = "TaxRate cannot be negative")
        @Digits(integer = 2, fraction = 4, message = "TaxRate has too many digits")
        BigDecimal taxRate
) {
}
