package com.hugo.huds.diagnosticcase.casepart.repository;

import com.hugo.huds.diagnosticcase.casepart.dto.PartsSupplyStatus;
import com.hugo.huds.diagnosticcase.casepart.entity.CasePart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CasePartRepository extends JpaRepository<CasePart, UUID> {

    /**
     * One row of the change feed. Archived parts are included on purpose.
     * The names of the creator and the editor are not part of the row: the service looks them up in one batch.
     */
    interface ChangeRow {
        UUID getCasePartId();

        UUID getDiagnosticCaseId();

        String getPartName();

        String getOemPartNo();

        BigDecimal getQuantity();

        String getUnit();

        String getSupplier();

        String getPartsSupplyStatus();

        LocalDate getOrderedOn();

        LocalDate getExpectedArrivalOn();

        LocalDate getArrivedOn();

        Integer getWarrantyMonths();

        UUID getInstalledVisitId();

        Long getRowVersion();

        Instant getCreatedAt();

        UUID getCreatedBy();

        Instant getUpdatedAt();

        UUID getUpdatedBy();

        Boolean getArchived();
    }

    /**
     * Whether the case still has a part that has to be waited for (the given statuses).
     * Archived parts are not counted: the entity restriction hides them.
     */
    boolean existsByDiagnosticCaseIdAndPartsSupplyStatusIn(UUID diagnosticCaseId, Collection<PartsSupplyStatus> statuses);

    /**
     * The active parts of a case.
     */
    List<CasePart> findByDiagnosticCaseId(UUID diagnosticCaseId);

    /**
     * Parts changed after the cursor (updated_at, case_part_id), oldest first.
     * Native SQL is needed because @SQLRestriction would hide archived rows from JPQL.
     */
    @Query(value = """
            select p.case_part_id as casePartId,
                   p.diagnostic_case_id as diagnosticCaseId,
                   p.part_name as partName,
                   p.oem_part_no as oemPartNo,
                   p.quantity as quantity,
                   p.unit as unit,
                   p.supplier as supplier,
                   p.parts_supply_status as partsSupplyStatus,
                   p.ordered_on as orderedOn,
                   p.expected_arrival_on as expectedArrivalOn,
                   p.arrived_on as arrivedOn,
                   p.warranty_months as warrantyMonths,
                   p.installed_visit_id as installedVisitId,
                   p.row_version as rowVersion,
                   p.created_at as createdAt,
                   p.created_by as createdBy,
                   p.updated_at as updatedAt,
                   p.updated_by as updatedBy,
                   (p.archived_at is not null) as archived
            from case_parts p
            where (p.updated_at, p.case_part_id) > (:since, :sinceId)
            order by p.updated_at, p.case_part_id
            limit :limit
            """, nativeQuery = true)
    List<ChangeRow> findChanges(@Param("since") Instant since,
                                @Param("sinceId") UUID sinceId,
                                @Param("limit") int limit);
}
