package com.hugo.huds.diagnosticcase.repository;

import com.hugo.huds.diagnosticcase.dto.WorkflowStatus;
import com.hugo.huds.diagnosticcase.entity.DiagnosticCase;
import com.hugo.huds.user.reponse.UserBriefResponse;
import org.hibernate.query.spi.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface DiagnosticCaseRepository extends JpaRepository<DiagnosticCase, UUID> {
    /**
     * One row of the change feed. Cases are never archived, so there is no archived flag.
     */
    interface ChangeRow {
        UUID getDiagnosticCaseId();

        String getCaseNo();

        UUID getCustomerId();

        UUID getServiceAddressId();

        UUID getApplianceId();

        // The jsonb column, read as text; the service turns it into a map.
        String getServiceAddressSnapshot();

        String getReportedSymptoms();

        String getPrimarySymptom();

        // The jsonb columns, read as text; the service turns them into JSON values.
        String getSecondarySymptoms();

        String getFailureTiming();

        String getWorkflowStatus();

        String getDisposition();

        String getHoldReason();

        UUID getRelatedCaseId();

        Instant getOpenedAt();

        Instant getClosedAt();

        Long getRowVersion();

        Instant getCreatedAt();

        UUID getCreatedBy();

        String getCreatedByName();

        Instant getUpdatedAt();

        UUID getUpdatedBy();

        String getUpdatedByName();

        default UserBriefResponse getCreator() {
            return getCreatedBy() == null ? null : new UserBriefResponse(getCreatedBy(), getCreatedByName());
        }

        default UserBriefResponse getEditor() {
            return getUpdatedBy() == null ? null : new UserBriefResponse(getUpdatedBy(), getUpdatedByName());
        }
    }

    /**
     * Cases changed after the cursor (updated_at, diagnostic_case_id), oldest first.
     */
    @Query(value = """
            select c.diagnostic_case_id as diagnosticCaseId,
                   c.case_no as caseNo,
                   c.customer_id as customerId,
                   c.service_address_id as serviceAddressId,
                   c.appliance_id as applianceId,
                   cast(c.service_address_snapshot as text) as serviceAddressSnapshot,
                   c.reported_symptoms as reportedSymptoms,
                   c.primary_symptom as primarySymptom,
                   cast(c.secondary_symptoms as text) as secondarySymptoms,
                   cast(c.failure_timing as text) as failureTiming,
                   c.workflow_status as workflowStatus,
                   c.disposition as disposition,
                   c.hold_reason as holdReason,
                   c.related_case_id as relatedCaseId,
                   c.opened_at as openedAt,
                   c.closed_at as closedAt,
                   c.row_version as rowVersion,
                   c.created_at as createdAt,
                   c.created_by as createdBy,
                   cu.user_name as createdByName,
                   c.updated_at as updatedAt,
                   c.updated_by as updatedBy,
                   uu.user_name as updatedByName
            from diagnostic_cases c
            left join users cu on cu.user_id = c.created_by
            left join users uu on uu.user_id = c.updated_by
            where (c.updated_at, c.diagnostic_case_id) > (:since, :sinceId)
            order by c.updated_at, c.diagnostic_case_id
            limit :limit
            """, nativeQuery = true)
    List<ChangeRow> findChanges(@Param("since") Instant since,
                                @Param("sinceId") UUID sinceId,
                                @Param("limit") int limit);

    boolean existsByApplianceId(UUID applianceId);

    boolean existsByDiagnosticCaseId(UUID diagnosticCaseId);
    /**
     * Whether the appliance already has a case that is not finished yet.
     *
     * @param finishedStatuses the states in which a case no longer counts (CLOSED, CANCELLED)
     */
    @Query("""
            select diagnosticCaseId from DiagnosticCase c
                        where c.applianceId=:applianceId
                           and c.customerId = :customerId
                           and c.workflowStatus not in :finishedStatuses
            """)
    Optional<UUID> findUnfinishedId(UUID applianceId, UUID customerId, Set<WorkflowStatus> finishedStatuses);
}
