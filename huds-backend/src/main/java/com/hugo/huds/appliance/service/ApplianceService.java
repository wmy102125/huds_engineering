package com.hugo.huds.appliance.service;

import com.hugo.huds.appliance.dto.*;
import com.hugo.huds.appliance.entity.Appliance;
import com.hugo.huds.appliance.entity.ApplianceModel;
import com.hugo.huds.appliance.entity.ApplianceServiceAddress;
import com.hugo.huds.appliance.repository.ApplianceModelRepository;
import com.hugo.huds.appliance.repository.ApplianceRepository;
import com.hugo.huds.appliance.repository.ApplianceServiceAddressRepository;
import com.hugo.huds.audit.dto.AuditAction;
import com.hugo.huds.audit.service.AuditService;
import com.hugo.huds.audit.service.ChangedFields;
import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.common.response.PageLimits;
import com.hugo.huds.common.response.PageResponse;
import com.hugo.huds.user.reponse.UserBriefResponse;
import com.hugo.huds.user.service.UserService;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private final ApplianceServiceAddressRepository serviceAddressRepository;
    private final AuditService auditService;
    private final UserService userService;
    // Do not use the SyncEntityType.APPLIANCE, It will lead to circular dependencies.
    private static final String AUDIT_ENTITY_TYPE = "APPLIANCE";

    public ApplianceService(ApplianceModelRepository applianceModelRepository, ApplianceRepository applianceRepository, ApplianceServiceAddressRepository serviceAddressRepository, AuditService auditService, UserService userService) {
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

        UUID applianceModelId = request.applianceModelId();
        // Whether applianceModel exists
        findActiveApplianceModel(applianceModelId);
        // A retried request with the same id: the appliance already exists, so return it as is.
        var existing = applianceRepository.findById(applianceId);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        String serialNo = normalize(request.serialNo());
        // There cannot be identical appliance
        Optional<Appliance> applianceOptional = applianceRepository.findByApplianceModelIdAndSerialNo(applianceModelId, serialNo);
        if (applianceOptional.isPresent()) {
            return toResponse(applianceOptional.get());
        }

        // add a new appliance model
        Appliance appliance = new Appliance();
        appliance.setApplianceId(applianceId);
        appliance.setApplianceModelId(applianceModelId);
        appliance.setIdentityNotes(request.identityNotes());
        appliance.setSerialNo(serialNo);
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
        UUID applianceModelId = request.applianceModelId();
        // Whether applianceModel exists
        findActiveApplianceModel(applianceModelId);

        Appliance appliance = findActive(applianceId);
        String serialNo = normalize(request.serialNo());
        if (applianceRepository.existsDuplicateExcluding(appliance.getApplianceModelId(), serialNo, applianceId)) {
            throw new ApplicationException(ErrorCode.DUPLICATE_RESOURCE, "An appliance with the same applianceModelId, serialNo and modelNo already exists. applianceModelId: " + request.applianceModelId() + ", serialNo: " + request.serialNo());
        }
        // Whether the appliance model exists
        findActiveApplianceModel(appliance.getApplianceId());

        checkVersion(appliance, rowVersion);

        // Compare before applying the change. Only the field names go to audit trail.
        ChangedFields changes = new ChangedFields()
                .track("applianceModelId", appliance.getApplianceModelId(), applianceModelId)
                .track("serialNo", appliance.getSerialNo(), serialNo);

        appliance.setApplianceModelId(applianceModelId);
        appliance.setSerialNo(serialNo);
        appliance.setIdentityNotes(request.identityNotes());
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
        appliance.archive(Instant.now());
        applianceRepository.flush();
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
                Sort.by("created_at", "applianceId").descending());
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
                Sort.by("created_at", "applianceId")).stream().map(this::toResponse).toList();
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
        // Searches appliance service addresses
        List<ApplianceServiceAddressResponse> addressResponses = serviceAddressRepository.findByApplianceId(applianceId)
                .stream().map(serviceAddress -> {
                    return new ApplianceServiceAddressResponse(
                            serviceAddress.getApplianceId(),
                            serviceAddress.getServiceAddressId(),
                            serviceAddress.getNote(),
                            serviceAddress.getRowVersion());

                }).toList();

        return new ApplianceDetailResponse(
                modelResponse,
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
                addressResponses);
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
            throw new ApplicationException(ErrorCode.DUPLICATE_RESOURCE, "The appliance has already been modified by someone else.", false, rowVersion);
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
