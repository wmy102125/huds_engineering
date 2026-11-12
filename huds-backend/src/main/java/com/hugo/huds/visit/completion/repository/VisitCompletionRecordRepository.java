package com.hugo.huds.visit.completion.repository;

import com.hugo.huds.user.reponse.UserBriefResponse;
import com.hugo.huds.visit.completion.entity.VisitCompletionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VisitCompletionRecordRepository extends JpaRepository<VisitCompletionRecord, UUID> {

    /**
     * One row of the change feed. A record is never archived, so there is no archived flag.
     * The JSON column is read as text and parsed by the service.
     */
    interface ChangeRow {
        UUID getVisitCompletionRecordId();

        UUID getServiceVisitId();

        String getVerificationChecks();

        String getFinalStatus();

        String getFixOutcome();

        String getGoogleReviewStatus();

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

    /** The record of a visit, if any (one per visit). */
    Optional<VisitCompletionRecord> findByServiceVisitId(UUID serviceVisitId);

    /**
     * Records changed after the cursor (updated_at, visit_completion_record_id), oldest first.
     */
    @Query(value = """
            select r.visit_completion_record_id   as visitCompletionRecordId,
                   r.service_visit_id             as serviceVisitId,
                   cast(r.verification_checks as text) as verificationChecks,
                   r.final_status                 as finalStatus,
                   r.fix_outcome                  as fixOutcome,
                   r.google_review_status         as googleReviewStatus,
                   r.created_at                   as createdAt,
                   r.updated_at                   as updatedAt,
                   r.created_by                   as createdBy,
                   cu.user_name                   as createdByName,
                   r.updated_by                   as updatedBy,
                   uu.user_name                   as updatedByName,
                   r.row_version                  as rowVersion
              from visit_completion_records r
              left join users cu on cu.user_id = r.created_by
              left join users uu on uu.user_id = r.updated_by
             where (r.updated_at, r.visit_completion_record_id) > (:since, :sinceId)
             order by r.updated_at, r.visit_completion_record_id
             limit :limit
            """, nativeQuery = true)
    List<ChangeRow> findChanges(@Param("since") Instant since,
                                @Param("sinceId") UUID sinceId,
                                @Param("limit") int limit);
}
