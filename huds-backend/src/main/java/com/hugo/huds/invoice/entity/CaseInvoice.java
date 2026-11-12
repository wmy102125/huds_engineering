package com.hugo.huds.invoice.entity;

import com.hugo.huds.auth.entity.AuthoredAuditableEntity;
import com.hugo.huds.invoice.dto.InvoiceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapped to the existing {@code case_invoices} table.
 *
 * <p>The database schema is managed by Flyway. Hibernate must not be used
 * to create or update this table.</p>
 *
 * <p>The bill of a case, one row per case. The subtotal, tax and total are generated columns of the database;
 * they are not mapped here and are read by the change feed query.</p>
 */
@Entity
@Table(name = "case_invoices")
@Getter
@Setter
public class CaseInvoice extends AuthoredAuditableEntity {

    /** Chosen by the caller (the client generates it offline), so it has no generator. */
    @Id
    @Column(name = "case_invoice_id", nullable = false, updatable = false)
    private UUID caseInvoiceId;

    @Column(name = "diagnostic_case_id", nullable = false, updatable = false)
    private UUID diagnosticCaseId;

    /** Sent on every insert, so it defaults to DRAFT. */
    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_status", nullable = false)
    private InvoiceStatus invoiceStatus = InvoiceStatus.DRAFT;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "service_call_fee", nullable = false)
    private BigDecimal serviceCallFee = BigDecimal.ZERO;

    @Column(name = "labour_fee", nullable = false)
    private BigDecimal labourFee = BigDecimal.ZERO;

    @Column(name = "parts_fee", nullable = false)
    private BigDecimal partsFee = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "tax_rate", nullable = false)
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Version
    @Column(name = "row_version", nullable = false)
    @Setter(AccessLevel.NONE)
    private Long rowVersion;

    public CaseInvoice() {
        // Required by JPA.
    }

    /** Service call fee + labour fee + parts fee, the same sum the database uses for the subtotal. */
    public BigDecimal subtotal() {
        return serviceCallFee.add(labourFee).add(partsFee);
    }
}
