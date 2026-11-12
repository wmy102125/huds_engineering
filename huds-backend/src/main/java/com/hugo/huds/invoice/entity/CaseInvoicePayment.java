package com.hugo.huds.invoice.entity;

import com.hugo.huds.auth.entity.AuthoredAuditableEntity;
import com.hugo.huds.invoice.dto.PaymentMethod;
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
 * JPA entity mapped to the existing {@code case_invoice_payments} table.
 *
 * <p>The database schema is managed by Flyway. Hibernate must not be used
 * to create or update this table.</p>
 *
 * <p>Money received for a bill: one row per payment.</p>
 */
@Entity
@Table(name = "case_invoice_payments")
@Getter
@Setter
public class CaseInvoicePayment extends AuthoredAuditableEntity {

    /** Chosen by the caller (the client generates it offline), so it has no generator. */
    @Id
    @Column(name = "case_invoice_payment_id", nullable = false, updatable = false)
    private UUID caseInvoicePaymentId;

    @Column(name = "case_invoice_id", nullable = false, updatable = false)
    private UUID caseInvoiceId;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @Column(name = "reference_no")
    private String referenceNo;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    @Setter(AccessLevel.NONE)
    private Long rowVersion;

    public CaseInvoicePayment() {
        // Required by JPA.
    }
}
