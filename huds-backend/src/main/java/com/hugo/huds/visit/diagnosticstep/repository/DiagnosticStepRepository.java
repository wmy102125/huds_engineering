package com.hugo.huds.visit.diagnosticstep.repository;

import com.hugo.huds.user.reponse.UserBriefResponse;
import com.hugo.huds.visit.diagnosticstep.entity.DiagnosticStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiagnosticStepRepository extends JpaRepository<DiagnosticStep, UUID> {

    /**
     * One row of the change feed. A step is never archived, so there is no archived flag.
     */
    interface ChangeRow {
        UUID getDiagnosticStepId();

        UUID getServiceVisitId();

        Integer getStepNo();

        String getHypothesis();

        String getTestTarget();

        String getPowerState();

        String getExpected();

        String getActual();

        String getResult();

        String getNextStep();

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

    /** The step of a visit with this number, if any (unique per visit). */
    Optional<DiagnosticStep> findByServiceVisitIdAndStepNo(UUID serviceVisitId, Integer stepNo);

    /**
     * Steps changed after the cursor (updated_at, diagnostic_step_id), oldest first.
     */
    @Query(value = """
            select s.diagnostic_step_id as diagnosticStepId,
                   s.service_visit_id   as serviceVisitId,
                   s.step_no            as stepNo,
                   s.hypothesis         as hypothesis,
                   s.test_target        as testTarget,
                   s.power_state        as powerState,
                   s.expected           as expected,
                   s.actual             as actual,
                   s.result             as result,
                   s.next_step          as nextStep,
                   s.created_at         as createdAt,
                   s.updated_at         as updatedAt,
                   s.created_by         as createdBy,
                   cu.user_name         as createdByName,
                   s.updated_by         as updatedBy,
                   uu.user_name         as updatedByName,
                   s.row_version        as rowVersion
              from diagnostic_steps s
              left join users cu on cu.user_id = s.created_by
              left join users uu on uu.user_id = s.updated_by
             where (s.updated_at, s.diagnostic_step_id) > (:since, :sinceId)
             order by s.updated_at, s.diagnostic_step_id
             limit :limit
            """, nativeQuery = true)
    List<ChangeRow> findChanges(@Param("since") Instant since,
                                @Param("sinceId") UUID sinceId,
                                @Param("limit") int limit);
}
