package com.hugo.huds.diagnosticcase.summary.repository;

import com.hugo.huds.diagnosticcase.summary.entity.CaseSummaryRecord;
import com.hugo.huds.user.reponse.UserBriefResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CaseSummaryRecordRepository extends JpaRepository<CaseSummaryRecord, UUID> {

    /**
     * One row of the change feed. A summary is never archived, so there is no archived flag.
     * The JSON column is read as text and parsed by the service.
     */
    interface ChangeRow {
        UUID getCaseSummaryRecordId();

        UUID getDiagnosticCaseId();

        String getDiagnosticOutcome();

        String getRepairStatus();

        String getCustomerDecision();

        String getFailureCategories();

        String getPrimaryFailureCategory();

        String getDataTags();

        String getTechnicianSummary();

        Instant getCreatedAt();

        Instant getUpdatedAt();

        UUID getCreatedBy();

        String getCreatedByName();

        UUID getUpdatedBy();

        String getUpdatedByName();

        Long getRowVersion();

        default UserBriefResponse getCreator() {
            return getCreatedBy() == null || getCreatedByName() == null
                    ? null : new UserBriefResponse(getCreatedBy(), getCreatedByName());
        }

        default UserBriefResponse getEditor() {
            return getUpdatedBy() == null || getUpdatedByName() == null
                    ? null : new UserBriefResponse(getUpdatedBy(), getUpdatedByName());
        }
    }

    /** The summary of a case, if any (one per case). */
    Optional<CaseSummaryRecord> findByDiagnosticCaseId(UUID diagnosticCaseId);

    /**
     * Summaries changed after the cursor (updated_at, case_summary_record_id), oldest first.
     */
    @Query(value = """
            select r.case_summary_record_id            as caseSummaryRecordId,
                   r.diagnostic_case_id                as diagnosticCaseId,
                   r.diagnostic_outcome                as diagnosticOutcome,
                   r.repair_status                     as repairStatus,
                   r.customer_decision                 as customerDecision,
                   cast(r.failure_categories as text)  as failureCategories,
                   r.primary_failure_category          as primaryFailureCategory,
                   r.data_tags                         as dataTags,
                   r.technician_summary                as technicianSummary,
                   r.created_at                        as createdAt,
                   r.updated_at                        as updatedAt,
                   r.created_by                        as createdBy,
                   cu.user_name                        as createdByName,
                   r.updated_by                        as updatedBy,
                   uu.user_name                        as updatedByName,
                   r.row_version                       as rowVersion
              from case_summary_records r
              left join users cu on cu.user_id = r.created_by
              left join users uu on uu.user_id = r.updated_by
             where (r.updated_at, r.case_summary_record_id) > (:since, :sinceId)
             order by r.updated_at, r.case_summary_record_id
             limit :limit
            """, nativeQuery = true)
    List<ChangeRow> findChanges(@Param("since") Instant since,
                                @Param("sinceId") UUID sinceId,
                                @Param("limit") int limit);
}
