package com.hugo.huds.diagnosticcase.casepart.entity;

import com.hugo.huds.diagnosticcase.casepart.dto.PartsSupplyStatus;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A part that a case needs. Archived rows are invisible to every entity query; the pull reads them with native SQL.
 *
 * NOTE: the audit fields below are written out here because I have not seen the base class of your other entities.
 * If they extend a common base class, delete the six audit fields and the version field and extend it instead.
 */
@Getter
@Setter
@Entity
@Table(name = "case_parts")
@EntityListeners(AuditingEntityListener.class)
@SQLRestriction("archived_at is null")
public class CasePart {

    // The client generates the id (it may be created offline), so there is no generator.
    @Id
    @Column(name = "case_part_id", nullable = false, updatable = false)
    private UUID casePartId;

    @Column(name = "diagnostic_case_id", nullable = false, updatable = false)
    private UUID diagnosticCaseId;

    @Column(name = "part_name", nullable = false)
    private String partName;

    @Column(name = "oem_part_no")
    private String oemPartNo;

    @Column(name = "quantity", nullable = false)
    private BigDecimal quantity;

    @Column(name = "unit")
    private String unit;

    @Column(name = "supplier")
    private String supplier;

    @Enumerated(EnumType.STRING)
    @Column(name = "parts_supply_status", nullable = false)
    private PartsSupplyStatus partsSupplyStatus;

    @Column(name = "ordered_on")
    private LocalDate orderedOn;

    @Column(name = "expected_arrival_on")
    private LocalDate expectedArrivalOn;

    @Column(name = "arrived_on")
    private LocalDate arrivedOn;

    @Column(name = "warranty_months")
    private Integer warrantyMonths;

    @Column(name = "installed_visit_id")
    private UUID installedVisitId;

    @Version
    @Column(name = "row_version", nullable = false)
    private Long rowVersion;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "archived_at")
    private Instant archivedAt;

    /** Soft delete: the row stays, so the pull can tell the other devices. */
    public void archive(Instant when) {
        this.archivedAt = when;
    }
}
