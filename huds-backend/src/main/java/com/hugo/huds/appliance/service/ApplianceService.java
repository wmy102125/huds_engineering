package com.hugo.huds.appliance.service;

import com.hugo.huds.address.repository.ServiceAddressRepository;
import com.hugo.huds.appliance.dto.ApplianceDetailResponse;
import com.hugo.huds.appliance.dto.ApplianceModelResponse;
import com.hugo.huds.appliance.dto.ApplianceRequest;
import com.hugo.huds.appliance.dto.ApplianceResponse;
import com.hugo.huds.appliance.entity.Appliance;
import com.hugo.huds.appliance.entity.ApplianceModel;
import com.hugo.huds.appliance.repository.ApplianceModelRepository;
import com.hugo.huds.appliance.repository.ApplianceRepository;
import com.hugo.huds.audit.dto.AuditAction;
import com.hugo.huds.audit.service.AuditService;
import com.hugo.huds.audit.service.ChangedFields;
import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.common.response.PageLimits;
import com.hugo.huds.common.response.PageResponse;
import com.hugo.huds.sync.dto.SyncPullResponse;
import com.hugo.huds.user.reponse.UserBriefResponse;
import com.hugo.huds.user.service.UserService;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;
import java.util.*;

import static com.hugo.huds.common.util.ParamNomalize.normalize;

/**
 * @author Meiyan Wang
 * @date 2026-10-18 6:45 p.m.
 */
@Validated
@Service
@Slf4j
@Transactional(readOnly = true)
public class ApplianceService {

    private final ApplianceModelRepository applianceModelRepository;
    private final ApplianceRepository applianceRepository;
    private final ServiceAddressRepository serviceAddressRepository;
    private final AuditService auditService;
    private final UserService userService;
    // Do not use the SyncEntityType.APPLIANCE, It will lead to circular dependencies.
    private static final String AUDIT_ENTITY_TYPE = "APPLIANCE";
    private static final String LINK_AUDIT_ENTITY_TYPE = "APPLIANCE_SERVICE_ADDRESS";


    public ApplianceService(ApplianceModelRepository applianceModelRepository, ApplianceRepository applianceRepository, ServiceAddressRepository serviceAddressRepository, AuditService auditService, UserService userService) {
        this.applianceModelRepository = applianceModelRepository;
        this.applianceRepository = applianceRepository;
        this.serviceAddressRepository = serviceAddressRepository;
        this.auditService = auditService;
        this.userService = userService;
    }

    /**
     * Creates an appliance with the id chosen by the caller (the client generated it offline).
     *
     * @param applianceId the id of the appliance
     * @param request     the appliance details
     * @return the created appliance
     */
    @Transactional
    public ApplianceResponse add(@NotNull UUID applianceId, @NotNull ApplianceRequest request) {
        // A retried request with the same id: the appliance already exists, so return it as is.
        var existing = applianceRepository.findById(applianceId);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        UUID applianceModelId = request.applianceModelId();
        findActiveApplianceModel(applianceModelId);

        String serialNo = normalize(request.serialNo());
        checkSerialVerified(serialNo, request.serialVerified());
        throwIfDuplicate(applianceModelId, serialNo, null);

        Appliance appliance = new Appliance();
        appliance.setApplianceId(applianceId);
        appliance.setApplianceModelId(applianceModelId);
        appliance.setIdentityNotes(request.identityNotes());
        appliance.setSerialNo(serialNo);
        appliance.setSerialVerified(Boolean.TRUE.equals(request.serialVerified()));
        // saveAndFlush so that the row_version and update_at timestamp are populated before building the response.
        applianceRepository.saveAndFlush(appliance);
        auditService.recordInTransaction(AuditAction.CREATE, AUDIT_ENTITY_TYPE, applianceId, Map.of());
        return toResponse(appliance);
    }

    /**
     * Updates an appliance ,provided the caller has seen the current version.
     *
     * @param applianceId the id of the appliance
     * @param rowVersion  the version on which the caller based on change
     * @param request     the new appliance details
     * @return the updated appliance
     */
    @Transactional
    public ApplianceResponse update(@NotNull UUID applianceId, @NotNull Long rowVersion, @NotNull ApplianceRequest request) {
        Appliance appliance = findActive(applianceId);
        checkVersion(appliance, rowVersion);

        UUID applianceModelId = request.applianceModelId();
        findActiveApplianceModel(applianceModelId);

        String serialNo = normalize(request.serialNo());
        checkSerialVerified(serialNo, request.serialVerified());
        throwIfDuplicate(applianceModelId, serialNo, applianceId);

        // Compare before applying the change. Only the field names go to the audit trail.
        ChangedFields changes = new ChangedFields()
                .track("applianceModelId", appliance.getApplianceModelId(), applianceModelId)
                .track("serialNo", appliance.getSerialNo(), serialNo)
                .track("serialVerified", appliance.getSerialVerified(), Boolean.TRUE.equals(request.serialVerified()))
                .track("identityNotes", appliance.getIdentityNotes(), request.identityNotes());

        appliance.setApplianceModelId(applianceModelId);
        appliance.setSerialNo(serialNo);
        appliance.setIdentityNotes(request.identityNotes());
        appliance.setSerialVerified(Boolean.TRUE.equals(request.serialVerified()));
        // Flush so that the row_version and update_at timestamp are new when the response is built.
        applianceRepository.flush();
        auditService.recordInTransaction(AuditAction.UPDATE, AUDIT_ENTITY_TYPE, applianceId, Map.of("changedFields", changes.names()));

        return toResponse(appliance);
    }

    /**
     * Soft-deletes appliance, Archive appliances are invisible to every entity query
     * (see @SQLRestriction at Appliance), so the second call for the same id reports "not found"
     *
     * @param applianceId the id of the appliance model to archive
     * @param rowVersion  the version on which the caller based on change
     */
    @Transactional
    public ApplianceResponse archive(@NotNull UUID applianceId, @NotNull Long rowVersion) {
        Appliance appliance = findActive(applianceId);
        checkVersion(appliance, rowVersion);

        // Phase 1 has no service case module yet. When it exists, reject the archive here
        // if the appliance still has cases.

        // Inside ApplianceService.archive, after checkVersion (needs a ServiceCaseRepository):
//        if (serviceCaseRepository.existsByApplianceId(applianceId)) {
//            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD,
//                    "The appliance still has service cases and cannot be archived.");
//        }

        appliance.archive(Instant.now());
        applianceRepository.flush();
        auditService.recordInTransaction(AuditAction.ARCHIVE, AUDIT_ENTITY_TYPE, applianceId, Map.of());
        return toResponse(appliance);
    }

    /**
     * Searches the appliance catalog.
     *
     * @param applianceModelId the applianceModelId to filter on
     * @param serialNo         part of the serialNo, or null/blank for no filter
     * @param page             the zero-based page index
     * @param size             the page size ,limit to {@value PageLimits#MAX_PAGE_SIZE}
     * @return the requested page of appliances
     */
    public PageResponse<ApplianceResponse> pageList(@NotNull UUID applianceModelId, String serialNo, int page, int size) {

        // The sort order is fixed on the server, so the client cannot sort by an arbitrary property.
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, PageLimits.MAX_PAGE_SIZE),
                Sort.by("createdAt", "applianceId").descending());
        return PageResponse.of(
                applianceRepository.findByFilters(applianceModelId, normalize(serialNo), pageable)
                        .map(this::toResponse));
    }

    /**
     * Searches models by applianceModelId, with optional serialNo number.
     *
     * @param applianceModelId the applianceModelId to filter on
     * @param serialNo         part of the serialNo, or null/blank for no filter
     * @return the requested list of models
     */
    public List<ApplianceResponse> allList(@NotNull UUID applianceModelId, String serialNo) {

        return applianceRepository.findAllByFilters(applianceModelId, normalize(serialNo),
                Sort.by("createdAt", "applianceId")).stream().map(this::toResponse).toList();
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

        List<ApplianceRepository.ChangeRow> rows = applianceRepository.findChanges(since, sinceId, limit + 1);
        // There is no change row, return default response
        if (CollectionUtils.isEmpty(rows)) {
            return SyncPullResponse.nullListResponse(since, sinceId);
        }

        boolean hasMore = rows.size() > limit;
        List<ApplianceRepository.ChangeRow> page = hasMore ? rows.subList(0, limit) : rows;

        List<ApplianceDetailResponse> items = page.stream().map(row -> new ApplianceDetailResponse(
                row.getApplianceId(),
                row.getApplianceModelId(),
                row.getSerialNo(),
                Boolean.TRUE.equals(row.getSerialVerified()),
                row.getIdentityNotes(),
                row.getRowVersion(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                row.getCreator(),
                row.getEditor(),
                Boolean.TRUE.equals(row.getArchived()))).toList();

        ApplianceRepository.ChangeRow last = page.get(page.size() - 1);
        return new SyncPullResponse(items, last.getUpdatedAt(), last.getApplianceId(), hasMore);
    }

    /**
     * Returns the details of an appliance
     *
     * @param applianceId the id of the appliance
     * @return the matching model with the user who created and last updated it
     * @throws ApplicationException with {@code NOT_FOUND} if no active has this id
     */
    public ApplianceDetailResponse detail(@NotNull UUID applianceId) {

        Appliance appliance = findActive(applianceId);
        Set<UUID> userIds = new HashSet<>();
        if (appliance.getCreatedBy() != null) {
            userIds.add(appliance.getCreatedBy());
        }
        if (appliance.getUpdatedBy() != null) {
            userIds.add(appliance.getUpdatedBy());
        }

        Map<UUID, UserBriefResponse> userBriefResponseMap = userService.findByUserIds(userIds);

        // Searches appliance model
        ApplianceModel model = findActiveApplianceModel(appliance.getApplianceModelId());
        ApplianceModelResponse modelResponse = new ApplianceModelResponse(model.getApplianceModelId(), model.getApplianceType(),
                model.getBrand(), model.getModelNo(), model.getRowVersion());

        return new ApplianceDetailResponse(
                applianceId,
                appliance.getApplianceModelId(),
                appliance.getSerialNo(),
                appliance.getSerialVerified(),
                appliance.getIdentityNotes(),
                appliance.getRowVersion(),
                appliance.getCreatedAt(),
                appliance.getUpdatedAt(),
                userBriefResponseMap.get(appliance.getCreatedBy()),
                userBriefResponseMap.get(appliance.getUpdatedBy()),
                appliance.getArchivedAt() != null);
    }

    private ApplianceModel findActiveApplianceModel(@NotNull UUID applianceModelId) {
        return applianceModelRepository.findById(applianceModelId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.NOT_FOUND, "Appliance model " + applianceModelId + " not found."));
    }

    private Appliance findActive(@NotNull UUID applianceId) {
        return applianceRepository.findById(applianceId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.NOT_FOUND, "Appliance " + applianceId + " not found."));
    }

    // Optimistic concurrency: the caller must have seen the current version
    private void checkVersion(@NotNull Appliance appliance, @NotNull Long rowVersion) {
        if (!Objects.equals(appliance.getRowVersion(), rowVersion)) {
            throw new ApplicationException(ErrorCode.VERSION_CONFLICT, "The appliance has already been modified by someone else.", false, appliance.getRowVersion());
        }
    }

    /**
     * Rejects an appliance that has the same model and serial number as another active appliance.
     * An appliance without a serial number can never be a duplicate.
     *
     * @param excludeId the appliance being updated, or null when creating
     */
    private void throwIfDuplicate(UUID applianceModelId, String serialNo, UUID excludeId) {
        if (serialNo.isEmpty()) {
            return;
        }
        Optional<Appliance> duplicate = applianceRepository.findBySerialNoIgnoreCase(applianceModelId, serialNo);
        if (duplicate.isPresent() && !duplicate.get().getApplianceId().equals(excludeId)) {
            throw ApplicationException.duplicate(
                    "An appliance with the same model and serial number already exists.",
                    duplicate.get().getApplianceId());
        }
    }

    /**
     * A serial number cannot be marked as verified when there is no serial number.
     *
     * @param serialNo       the normalized serial number, empty if missing
     * @param serialVerified whether the caller marked the serial number as verified
     */
    private void checkSerialVerified(String serialNo, Boolean serialVerified) {
        if (Boolean.TRUE.equals(serialVerified) && serialNo.isEmpty()) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD,
                    "serialVerified cannot be true when serialNo is empty.");
        }
    }

    private ApplianceResponse toResponse(Appliance appliance) {
        return new ApplianceResponse(appliance.getApplianceId(),
                appliance.getApplianceModelId(),
                appliance.getSerialNo(),
                appliance.getSerialVerified(),
                appliance.getIdentityNotes(),
                appliance.getRowVersion());
    }
}
