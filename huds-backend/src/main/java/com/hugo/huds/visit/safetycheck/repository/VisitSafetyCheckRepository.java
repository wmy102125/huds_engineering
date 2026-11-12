package com.hugo.huds.visit.safetycheck.repository;

import com.hugo.huds.user.reponse.UserBriefResponse;
import com.hugo.huds.visit.safetycheck.entity.VisitSafetyCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface VisitSafetyCheckRepository extends JpaRepository<VisitSafetyCheck, UUID> {

    /**
     * One row of the change feed. The record is never archived, so there is no archived flag.
     * The JSON columns are read as text and parsed by the service.
     */
    interface ChangeRow {
        UUID getServiceVisitId();

        String getSafetyChecks();

        String getUtilityBaseline();

        String getEnvironmentBaseline();

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

    /**
     * Safety checks changed after the cursor (updated_at, service_visit_id), oldest first.
     */
    @Query(value = """
            select s.service_visit_id                  as serviceVisitId,
                   cast(s.safety_checks as text)        as safetyChecks,
                   cast(s.utility_baseline as text)     as utilityBaseline,
                   cast(s.environment_baseline as text) as environmentBaseline,
                   s.created_at                         as createdAt,
                   s.updated_at                         as updatedAt,
                   s.created_by                         as createdBy,
                   cu.user_name                         as createdByName,
                   s.updated_by                         as updatedBy,
                   uu.user_name                         as updatedByName,
                   s.row_version                        as rowVersion
              from visit_safety_checks s
              left join users cu on cu.user_id = s.created_by
              left join users uu on uu.user_id = s.updated_by
             where (s.updated_at, s.service_visit_id) > (:since, :sinceId)
             order by s.updated_at, s.service_visit_id
             limit :limit
            """, nativeQuery = true)
    List<ChangeRow> findChanges(@Param("since") Instant since,
                                @Param("sinceId") UUID sinceId,
                                @Param("limit") int limit);
}
