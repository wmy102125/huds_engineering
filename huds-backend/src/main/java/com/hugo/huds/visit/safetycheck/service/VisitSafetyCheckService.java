package com.hugo.huds.visit.safetycheck.service;

import com.hugo.huds.audit.dto.AuditAction;
import com.hugo.huds.audit.service.AuditService;
import com.hugo.huds.audit.service.ChangedFields;
import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.common.util.JsonFieldSupport;
import com.hugo.huds.sync.dto.SyncPullResponse;
import com.hugo.huds.visit.dto.VisitStatus;
import com.hugo.huds.visit.entity.ServiceVisit;
import com.hugo.huds.visit.repository.ServiceVisitRepository;
import com.hugo.huds.visit.safetycheck.dto.VisitSafetyCheckDetailResponse;
import com.hugo.huds.visit.safetycheck.dto.VisitSafetyCheckRequest;
import com.hugo.huds.visit.safetycheck.entity.VisitSafetyCheck;
import com.hugo.huds.visit.safetycheck.repository.VisitSafetyCheckRepository;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.hugo.huds.common.util.AuthorityCheck.hasAuthority;

/**
 * Business operations for the safety and baseline checks of a visit (section 2 of the diagnostic form).
 *
 * <p>There is one record per visit and its id is the id of the visit. It is written by the assigned technician
 * (or an administrator) while the technician is on site. A finished visit is read only.</p>
 */
@Validated
@Service
@Transactional(readOnly = true)
public class VisitSafetyCheckService {

    private static final String AUDIT_ENTITY_TYPE = "VISIT_SAFETY_CHECK";
    private static final String VISIT_ASSIGN = "VISIT_ASSIGN";

    /** The checks are written on site: while the visit is in progress or paused. */
    private static final Set<VisitStatus> WRITABLE_VISIT_STATUSES =
            EnumSet.of(VisitStatus.IN_PROGRESS, VisitStatus.PAUSED);

    private final VisitSafetyCheckRepository visitSafetyCheckRepository;
    private final ServiceVisitRepository serviceVisitRepository;
    private final AuditService auditService;
    private final JsonFieldSupport jsonFieldSupport;

    public VisitSafetyCheckService(VisitSafetyCheckRepository visitSafetyCheckRepository,
                                   ServiceVisitRepository serviceVisitRepository,
                                   AuditService auditService,
                                   JsonFieldSupport jsonFieldSupport) {
        this.visitSafetyCheckRepository = visitSafetyCheckRepository;
        this.serviceVisitRepository = serviceVisitRepository;
        this.auditService = auditService;
        this.jsonFieldSupport = jsonFieldSupport;
    }

    /**
     * Records the checks of a visit. The id of the record is the id of the visit.
     *
     * @param actorUserId    the user who sends the change
     * @param serviceVisitId the id of the visit (and of the new record)
     * @param request        the three JSON objects
     * @return the row version of the new row
     */
    @Transactional
    public Long add(@NotNull UUID actorUserId, @NotNull UUID serviceVisitId, @NotNull VisitSafetyCheckRequest request) {
        // A retried request with the same id: the row already exists, so return it as is.
        Optional<VisitSafetyCheck> existing = visitSafetyCheckRepository.findById(serviceVisitId);
        if (existing.isPresent()) {
            return existing.get().getRowVersion();
        }

        // Check the JSON fields first, so a bad payload is refused before anything is written.
        String safetyChecks = jsonFieldSupport.objectText(request.safetyChecks(), "safetyChecks");
        String utilityBaseline = jsonFieldSupport.objectText(request.utilityBaseline(), "utilityBaseline");
        String environmentBaseline = jsonFieldSupport.objectText(request.environmentBaseline(), "environmentBaseline");

        requireWritableVisit(actorUserId, serviceVisitId);

        VisitSafetyCheck check = new VisitSafetyCheck();
        check.setServiceVisitId(serviceVisitId);
        check.setSafetyChecks(safetyChecks);
        check.setUtilityBaseline(utilityBaseline);
        check.setEnvironmentBaseline(environmentBaseline);
        // saveAndFlush so that the row_version and audit timestamps are populated before they are read.
        visitSafetyCheckRepository.saveAndFlush(check);
        auditService.recordInTransaction(AuditAction.CREATE, AUDIT_ENTITY_TYPE, serviceVisitId, Map.of());
        return check.getRowVersion();
    }

    /**
     * Changes the checks of a visit, provided the caller has seen the current version.
     * A JSON object that is missing from the request stays as it is.
     *
     * @param actorUserId    the user who sends the change
     * @param serviceVisitId the id of the visit (and of the record)
     * @param rowVersion     the version on which the caller based the change
     * @param request        the new values
     * @return the new row version
     */
    @Transactional
    public Long update(@NotNull UUID actorUserId, @NotNull UUID serviceVisitId, @NotNull Long rowVersion,
                       @NotNull VisitSafetyCheckRequest request) {
        VisitSafetyCheck check = visitSafetyCheckRepository.findById(serviceVisitId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.NOT_FOUND,
                        "The safety checks of visit " + serviceVisitId + " were not found."));
        if (!Objects.equals(check.getRowVersion(), rowVersion)) {
            throw new ApplicationException(ErrorCode.VERSION_CONFLICT,
                    "The safety checks were changed by someone else.", false, check.getRowVersion());
        }

        // Missing means "unchanged".
        String safetyChecks = textOrNull(request.safetyChecks(), "safetyChecks");
        String utilityBaseline = textOrNull(request.utilityBaseline(), "utilityBaseline");
        String environmentBaseline = textOrNull(request.environmentBaseline(), "environmentBaseline");

        requireWritableVisit(actorUserId, serviceVisitId);

        // Compare the JSON as values, because PostgreSQL stores its own spacing of the text.
        ChangedFields changedFields = new ChangedFields()
                .track("safetyChecks", jsonFieldSupport.read(check.getSafetyChecks(), false),
                        valueOrOld(safetyChecks, check.getSafetyChecks()))
                .track("utilityBaseline", jsonFieldSupport.read(check.getUtilityBaseline(), false),
                        valueOrOld(utilityBaseline, check.getUtilityBaseline()))
                .track("environmentBaseline", jsonFieldSupport.read(check.getEnvironmentBaseline(), false),
                        valueOrOld(environmentBaseline, check.getEnvironmentBaseline()));

        if (safetyChecks != null) {
            check.setSafetyChecks(safetyChecks);
        }
        if (utilityBaseline != null) {
            check.setUtilityBaseline(utilityBaseline);
        }
        if (environmentBaseline != null) {
            check.setEnvironmentBaseline(environmentBaseline);
        }
        // Flush so that the row_version is the new one when it is read.
        visitSafetyCheckRepository.flush();
        auditService.recordInTransaction(AuditAction.UPDATE, AUDIT_ENTITY_TYPE, serviceVisitId,
                Map.of("changedFields", changedFields.names()));
        return check.getRowVersion();
    }

    /**
     * Searches the change rows by since, sinceId and limit.
     *
     * @param since   the change rows start time
     * @param sinceId the change rows start id
     * @param limit   this search batch max size number
     * @return matching rows
     */
    public SyncPullResponse fetchChanges(Instant since, UUID sinceId, int limit) {
        List<VisitSafetyCheckRepository.ChangeRow> rows = visitSafetyCheckRepository.findChanges(since, sinceId, limit + 1);
        // There is no change row, return default response
        if (CollectionUtils.isEmpty(rows)) {
            return SyncPullResponse.nullListResponse(since, sinceId);
        }

        boolean hasMore = rows.size() > limit;
        List<VisitSafetyCheckRepository.ChangeRow> page = hasMore ? rows.subList(0, limit) : rows;

        List<VisitSafetyCheckDetailResponse> responses = page.stream().map(row -> new VisitSafetyCheckDetailResponse(
                row.getServiceVisitId(),
                jsonFieldSupport.read(row.getSafetyChecks(), false),
                jsonFieldSupport.read(row.getUtilityBaseline(), false),
                jsonFieldSupport.read(row.getEnvironmentBaseline(), false),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                row.getCreator(),
                row.getEditor(),
                row.getRowVersion())).toList();

        VisitSafetyCheckRepository.ChangeRow last = page.get(page.size() - 1);
        return new SyncPullResponse(responses, last.getUpdatedAt(), last.getServiceVisitId(), hasMore);
    }

    /**
     * The visit must exist and be on site (in progress or paused), and the caller must be the assigned
     * technician or an administrator.
     */
    private void requireWritableVisit(UUID actorUserId, UUID serviceVisitId) {
        ServiceVisit visit = serviceVisitRepository.findById(serviceVisitId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.INVALID_PAYLOAD,
                        "The visit: " + serviceVisitId + " does not exist."));
        if (!Objects.equals(visit.getTechnicianId(), actorUserId) && !hasAuthority(VISIT_ASSIGN)) {
            throw new ApplicationException(ErrorCode.FORBIDDEN, "This visit is assigned to another technician.");
        }
        if (!WRITABLE_VISIT_STATUSES.contains(visit.getVisitStatus())) {
            throw new ApplicationException(ErrorCode.DOMAIN_RULE_VIOLATION,
                    "The visit is " + visit.getVisitStatus() + ", its safety checks can only be written while the "
                            + "technician is on site.");
        }
    }

    /** The compact text of the object, or null when the request does not contain it (unchanged). */
    private String textOrNull(JsonNode value, String fieldName) {
        return value == null || value.isNull() ? null : jsonFieldSupport.objectText(value, fieldName);
    }

    /** The new value when there is one, otherwise the old one; both as JSON values for the comparison. */
    private JsonNode valueOrOld(String newText, String oldText) {
        return jsonFieldSupport.read(newText == null ? oldText : newText, false);
    }
}
