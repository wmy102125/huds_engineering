package com.hugo.huds.visit.repository;

import com.hugo.huds.user.reponse.UserBriefResponse;
import com.hugo.huds.visit.dto.VisitStatus;
import com.hugo.huds.visit.entity.ServiceVisit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ServiceVisitRepository extends JpaRepository<ServiceVisit, UUID> {

    /** True if the case has any visit at all. **/
    boolean existsByDiagnosticCaseId(UUID diagnosticCaseId);

    /** True if the case has a visit that was not cancelled. Used to tell the first visit from a later one. */
    boolean existsByDiagnosticCaseIdAndVisitStatusNot(UUID diagnosticCaseId, VisitStatus visitStatus);

    Optional<ServiceVisit> findFirstByDiagnosticCaseIdAndVisitStatusIn(UUID diagnosticCaseId, Set<VisitStatus> visitStatuses);

    @Query(value = """
            select v.service_visit_id   as serviceVisitId,
                   v.diagnostic_case_id as diagnosticCaseId,
                   v.technician_id      as technicianId,
                   t.user_name          as technicianName,
                   v.visit_status       as visitStatus,
                   v.visit_kind         as visitKind,
                   v.scheduled_at       as scheduledAt,
                   v.started_at         as startedAt,
                   v.ended_at           as endedAt,
                   v.notes              as notes,
                   v.created_at         as createdAt,
                   v.updated_at         as updatedAt,
                   v.created_by         as createdBy,
                   c.user_name          as createdByName,
                   v.updated_by         as updatedBy,
                   e.user_name          as updatedByName,
                   v.row_version        as rowVersion
              from service_visits v
              left join users t on t.user_id = v.technician_id
              left join users c on c.user_id = v.created_by
              left join users e on e.user_id = v.updated_by
             where (v.updated_at, v.service_visit_id) > (:since, :sinceId)
             order by v.updated_at, v.service_visit_id
             limit :limit
            """, nativeQuery = true)
    List<ChangeRow> findChanges(@Param("since") Instant since,
                                @Param("sinceId") UUID sinceId,
                                @Param("limit") int limit);

    interface ChangeRow {
        UUID getServiceVisitId();

        UUID getDiagnosticCaseId();

        UUID getTechnicianId();

        String getTechnicianName();

        String getVisitStatus();

        String getVisitKind();

        Instant getScheduledAt();

        Instant getStartedAt();

        Instant getEndedAt();

        String getNotes();

        Instant getCreatedAt();

        Instant getUpdatedAt();

        UUID getCreatedBy();

        String getCreatedByName();

        UUID getUpdatedBy();

        String getUpdatedByName();

        Long getRowVersion();

        default UserBriefResponse getTechnician() {
            return getTechnicianId() == null || getTechnicianName() == null
                    ? null : new UserBriefResponse(getTechnicianId(), getTechnicianName());
        }

        default UserBriefResponse getCreator() {
            return getCreatedBy() == null || getCreatedByName() == null
                    ? null : new UserBriefResponse(getCreatedBy(), getCreatedByName());
        }

        default UserBriefResponse getEditor() {
            return getUpdatedBy() == null || getUpdatedByName() == null
                    ? null : new UserBriefResponse(getUpdatedBy(), getUpdatedByName());
        }
    }
}
