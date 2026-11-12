package com.hugo.huds.diagnosticcase.repository;

import com.hugo.huds.diagnosticcase.dto.ErrorCodeStatus;
import com.hugo.huds.diagnosticcase.entity.CaseErrorCode;
import com.hugo.huds.user.reponse.UserBriefResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CaseErrorCodeRepository extends JpaRepository<CaseErrorCode, UUID> {

    /**
     * One row of the change feed. Error codes are never archived, so there is no archived flag.
     */
    interface ChangeRow {
        UUID getCaseErrorCodeId();

        UUID getDiagnosticCaseId();

        String getErrorCode();

        String getDisplayMessage();

        String getCodeStatus();

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
     * Finds the error code of a case with the same code (ignoring case) and status.
     * Matches the unique index of the table.
     *
     * @param diagnosticCaseId the id of the case
     * @param errorCode        the code as typed
     * @param codeStatus       the status of the code
     * @return the existing row, or empty if there is none
     */
    @Query("""
            select e from CaseErrorCode e
            where e.diagnosticCaseId = :diagnosticCaseId
              and upper(e.errorCode) = upper(:errorCode)
              and e.codeStatus = :codeStatus
            """)
    Optional<CaseErrorCode> findDuplicate(@Param("diagnosticCaseId") UUID diagnosticCaseId,
                                          @Param("errorCode") String errorCode,
                                          @Param("codeStatus") ErrorCodeStatus codeStatus);

    /**
     * Error codes changed after the cursor (updated_at, case_error_code_id), oldest first.
     */
    @Query(value = """
            select e.case_error_code_id as caseErrorCodeId,
                   e.diagnostic_case_id as diagnosticCaseId,
                   e.error_code         as errorCode,
                   e.display_message    as displayMessage,
                   e.code_status        as codeStatus,
                   e.created_at         as createdAt,
                   e.updated_at         as updatedAt,
                   e.created_by         as createdBy,
                   cu.user_name         as createdByName,
                   e.updated_by         as updatedBy,
                   uu.user_name         as updatedByName,
                   e.row_version        as rowVersion
              from case_error_codes e
              left join users cu on cu.user_id = e.created_by
              left join users uu on uu.user_id = e.updated_by
             where (e.updated_at, e.case_error_code_id) > (:since, :sinceId)
             order by e.updated_at, e.case_error_code_id
             limit :limit
            """, nativeQuery = true)
    List<ChangeRow> findChanges(@Param("since") Instant since,
                                @Param("sinceId") UUID sinceId,
                                @Param("limit") int limit);
}
