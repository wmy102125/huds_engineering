package com.hugo.huds.visit.completion.service;

import com.hugo.huds.audit.dto.AuditAction;
import com.hugo.huds.audit.service.AuditService;
import com.hugo.huds.audit.service.ChangedFields;
import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.common.util.JsonFieldSupport;
import com.hugo.huds.sync.dto.SyncPullResponse;
import com.hugo.huds.visit.completion.dto.FinalStatus;
import com.hugo.huds.visit.completion.dto.FixOutcome;
import com.hugo.huds.visit.completion.dto.GoogleReviewStatus;
import com.hugo.huds.visit.completion.dto.VisitCompletionDetailResponse;
import com.hugo.huds.visit.completion.dto.VisitCompletionRequest;
import com.hugo.huds.visit.completion.entity.VisitCompletionRecord;
import com.hugo.huds.visit.completion.repository.VisitCompletionRecordRepository;
import com.hugo.huds.visit.dto.VisitStatus;
import com.hugo.huds.visit.entity.ServiceVisit;
import com.hugo.huds.visit.repository.ServiceVisitRepository;
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

/**
 * Business operations for the completion record of a visit (section 5 of the diagnostic form).
 *
 * <p>There is one record per visit. It is written by the assigned technician (or an administrator) while the
 * technician is still on site, so before the visit is completed. A finished visit is read only.</p>
 */
@Validated
@Service
@Transactional(readOnly = true)
public class VisitCompletionService {

    private static final String AUDIT_ENTITY_TYPE = "VISIT_COMPLETION";
    private static final String VISIT_ASSIGN = "VISIT_ASSIGN";

    /** The record is written on site: while the visit is in progress or paused. */
    private static final Set<VisitStatus> WRITABLE_VISIT_STATUSES =
            EnumSet.of(VisitStatus.IN_PROGRESS, VisitStatus.PAUSED);

    private final VisitCompletionRecordRepository visitCompletionRecordRepository;
    private final ServiceVisitRepository serviceVisitRepository;
    private final AuditService auditService;
    private final JsonFieldSupport jsonFieldSupport;

    public VisitCompletionService(VisitCompletionRecordRepository visitCompletionRecordRepository,
                                  ServiceVisitRepository serviceVisitRepository,
                                  AuditService auditService,
                                  JsonFieldSupport jsonFieldSupport) {
        this.visitCompletionRecordRepository = visitCompletionRecordRepository;
        this.serviceVisitRepository = serviceVisitRepository;
        this.auditService = auditService;
        this.jsonFieldSupport = jsonFieldSupport;
    }

    /**
     * Adds the completion record of a visit, with the id chosen by the caller (the client generates it offline).
     *
     * @param actorUserId              the user who sends the change
     * @param visitCompletionRecordId  the id of the new record
     * @param request                  the record details
     * @return the row version of the new row
     */
    @Transactional
    public Long add(@NotNull UUID actorUserId, @NotNull UUID visitCompletionRecordId,
                    @NotNull VisitCompletionRequest request) {
        // A retried request with the same id: the row already exists, so return it as is.
        Optional<VisitCompletionRecord> existing = visitCompletionRecordRepository.findById(visitCompletionRecordId);
        if (existing.isPresent()) {
            return existing.get().getRowVersion();
        }

        // Check the JSON field first, so a bad payload is refused before anything is written.
        String verificationChecks = jsonFieldSupport.objectText(request.verificationChecks(), "verificationChecks");

        requireWritableVisit(actorUserId, request.serviceVisitId());

        // One record per visit.
        Optional<VisitCompletionRecord> sameVisit = visitCompletionRecordRepository.findByServiceVisitId(request.serviceVisitId());
        if (sameVisit.isPresent()) {
            throw ApplicationException.duplicate("The visit already has a completion record.",
                    sameVisit.get().getVisitCompletionRecordId());
        }

        VisitCompletionRecord record = new VisitCompletionRecord();
        record.setVisitCompletionRecordId(visitCompletionRecordId);
        record.setServiceVisitId(request.serviceVisitId());
        record.setVerificationChecks(verificationChecks);
        record.setFinalStatus(request.finalStatus());
        record.setFixOutcome(request.fixOutcome());
        record.setGoogleReviewStatus(request.googleReviewStatus());
        // saveAndFlush so that the row_version and audit timestamps are populated before they are read.
        visitCompletionRecordRepository.saveAndFlush(record);
        auditService.recordInTransaction(AuditAction.CREATE, AUDIT_ENTITY_TYPE, visitCompletionRecordId, Map.of());
        return record.getRowVersion();
    }

    /**
     * Changes the completion record of a visit that is on site, provided the caller has seen the current version.
     * The visit of a record never changes. A missing checks object means "unchanged"; the three status
     * fields are replaced as sent.
     *
     * @param actorUserId             the user who sends the change
     * @param visitCompletionRecordId the id of the record
     * @param rowVersion              the version on which the caller based the change
     * @param request                 the new values
     * @return the new row version
     */
    @Transactional
    public Long update(@NotNull UUID actorUserId, @NotNull UUID visitCompletionRecordId, @NotNull Long rowVersion,
                       @NotNull VisitCompletionRequest request) {
        VisitCompletionRecord record = visitCompletionRecordRepository.findById(visitCompletionRecordId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.NOT_FOUND,
                        "Visit completion record " + visitCompletionRecordId + " not found."));
        if (!Objects.equals(record.getRowVersion(), rowVersion)) {
            throw new ApplicationException(ErrorCode.VERSION_CONFLICT,
                    "The completion record was changed by someone else.", false, record.getRowVersion());
        }
        if (!Objects.equals(record.getServiceVisitId(), request.serviceVisitId())) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD, "The visit of a completion record cannot be changed.");
        }

        // Missing means "unchanged".
        JsonNode checksNode = request.verificationChecks();
        String verificationChecks = checksNode == null || checksNode.isNull()
                ? null : jsonFieldSupport.objectText(checksNode, "verificationChecks");
        FinalStatus finalStatus = request.finalStatus();
        FixOutcome fixOutcome = request.fixOutcome();
        GoogleReviewStatus googleReviewStatus = request.googleReviewStatus();

        requireWritableVisit(actorUserId, record.getServiceVisitId());

        // Compare the JSON as values, because PostgreSQL stores its own spacing of the text.
        ChangedFields changedFields = new ChangedFields()
                .track("verificationChecks", jsonFieldSupport.read(record.getVerificationChecks(), false),
                        jsonFieldSupport.read(verificationChecks == null ? record.getVerificationChecks() : verificationChecks, false))
                .track("finalStatus", record.getFinalStatus(), finalStatus)
                .track("fixOutcome", record.getFixOutcome(), fixOutcome)
                .track("googleReviewStatus", record.getGoogleReviewStatus(), googleReviewStatus);

        if (verificationChecks != null) {
            record.setVerificationChecks(verificationChecks);
        }
        record.setFinalStatus(finalStatus);
        record.setFixOutcome(fixOutcome);
        record.setGoogleReviewStatus(googleReviewStatus);
        // Flush so that the row_version is the new one when it is read.
        visitCompletionRecordRepository.flush();
        auditService.recordInTransaction(AuditAction.UPDATE, AUDIT_ENTITY_TYPE, visitCompletionRecordId,
                Map.of("changedFields", changedFields.names()));
        return record.getRowVersion();
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
        List<VisitCompletionRecordRepository.ChangeRow> rows =
                visitCompletionRecordRepository.findChanges(since, sinceId, limit + 1);
        // There is no change row, return default response
        if (CollectionUtils.isEmpty(rows)) {
            return SyncPullResponse.nullListResponse(since, sinceId);
        }

        boolean hasMore = rows.size() > limit;
        List<VisitCompletionRecordRepository.ChangeRow> page = hasMore ? rows.subList(0, limit) : rows;

        List<VisitCompletionDetailResponse> responses = page.stream().map(row -> new VisitCompletionDetailResponse(
                row.getVisitCompletionRecordId(),
                row.getServiceVisitId(),
                jsonFieldSupport.read(row.getVerificationChecks(), false),
                row.getFinalStatus() == null ? null : FinalStatus.valueOf(row.getFinalStatus()),
                row.getFixOutcome() == null ? null : FixOutcome.valueOf(row.getFixOutcome()),
                row.getGoogleReviewStatus() == null ? null : GoogleReviewStatus.valueOf(row.getGoogleReviewStatus()),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                row.getCreator(),
                row.getEditor(),
                row.getRowVersion())).toList();

        VisitCompletionRecordRepository.ChangeRow last = page.get(page.size() - 1);
        return new SyncPullResponse(responses, last.getUpdatedAt(), last.getVisitCompletionRecordId(), hasMore);
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
                    "The visit is " + visit.getVisitStatus() + ", its completion record can only be written while the "
                            + "technician is on site.");
        }
    }

    private boolean hasAuthority(String authority) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }
}
