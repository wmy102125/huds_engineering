package com.hugo.huds.appliance.service;

import com.hugo.huds.appliance.dto.ApplianceModelDetailResponse;
import com.hugo.huds.appliance.dto.ApplianceModelRequest;
import com.hugo.huds.appliance.dto.ApplianceModelResponse;
import com.hugo.huds.appliance.dto.ApplianceModelType;
import com.hugo.huds.appliance.entity.ApplianceModel;
import com.hugo.huds.appliance.repository.ApplianceModelRepository;
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
public class ApplianceModelService {

    private final ApplianceModelRepository applianceModelRepository;
    private final AuditService auditService;
    private final UserService userService;
    // Do not use the SyncEntityType.CUSTOMER, It will lead to circular dependencies.
    private static final String AUDIT_ENTITY_TYPE = "APPLIANCE_MODEL";

    public ApplianceModelService(ApplianceModelRepository applianceModelRepository, AuditService auditService, UserService userService) {
        this.applianceModelRepository = applianceModelRepository;
        this.auditService = auditService;
        this.userService = userService;
    }

    /**
     * Creates an appliance model with the id chosen by the caller (the client generated it offline).
     *
     * @param applianceModelId the id of the appliance model
     * @param modelRequest     the appliance model details
     * @return the created appliance model
     */
    @Transactional
    public ApplianceModelResponse add(@NotNull UUID applianceModelId, @NotNull ApplianceModelRequest modelRequest) {

        // There cannot be identical appliance
        Optional<ApplianceModel> modelOptional = applianceModelRepository.findByApplianceTypeAndBrandAndModelNo(modelRequest.applianceType(), modelRequest.brand(), modelRequest.modelNo());
        if (modelOptional.isPresent()) {
            return toResponse(modelOptional.get());
        }

        // add a new appliance model
        ApplianceModel applianceModel = new ApplianceModel();
        BeanUtils.copyProperties(modelRequest, applianceModel);
        applianceModel.setApplianceModelId(applianceModelId);
        // saveAndFlush so that the row_version and update_at timestamp are populated before building the response.
        applianceModelRepository.saveAndFlush(applianceModel);
        auditService.recordInTransaction(AuditAction.CREATE, AUDIT_ENTITY_TYPE, applianceModelId, Map.of());
        return toResponse(applianceModel);
    }

    /**
     * Updates an appliance model ,provided the caller has seen the current version.
     *
     * @param applianceModelId the id of the appliance model
     * @param rowVersion       the version on which the caller based on change
     * @param modelRequest     the new appliance model details
     * @return the updated appliance model
     */
    @Transactional
    public ApplianceModelResponse update(@NotNull UUID applianceModelId, @NotNull Long rowVersion, @NotNull ApplianceModelRequest modelRequest) {

        ApplianceModel applianceModel = findActive(applianceModelId);
        if (applianceModelRepository.existsDuplicateExcluding(applianceModel.getApplianceType(), applianceModel.getBrand(), applianceModel.getModelNo(), applianceModelId)) {
            throw new ApplicationException(ErrorCode.DUPLICATE_RESOURCE, "An appliance model with the same applianceType, brand and modelNo already exists. applianceModelId: " + applianceModelId + " applianceType: " + applianceModel.getApplianceType() + ", the brand: " + applianceModel.getBrand() + ", modelNo: " + applianceModel.getModelNo());
        }
        checkVersion(applianceModel, rowVersion);

        // Compare before applying the change. Only the field names go to audit trail.
        ChangedFields changes = new ChangedFields()
                .track("brand", applianceModel.getBrand(), modelRequest.brand())
                .track("applianceType", applianceModel.getApplianceType(), modelRequest.applianceType())
                .track("phone", applianceModel.getModelNo(), modelRequest.modelNo());

        applianceModel.setApplianceType(modelRequest.applianceType());
        applianceModel.setModelNo(modelRequest.modelNo());
        applianceModel.setBrand(modelRequest.brand());
        // Flush so that the row_version and update_at timestamp are new when the response is built.
        applianceModelRepository.flush();
        auditService.recordInTransaction(AuditAction.UPDATE, AUDIT_ENTITY_TYPE, applianceModelId, Map.of("changedFields", changes.names()));

        return toResponse(applianceModel);
    }

    /**
     * Soft-deletes appliance model, Archive appliance models are invisible to every entity query
     * (see @SQLRestriction at ApplianceModel), so the second call for the same id reports "not found"
     *
     * @param applianceModelId the id of the appliance model to archive
     * @param rowVersion       the version on which the caller based on change
     */
    public ApplianceModelResponse archive(@NotNull UUID applianceModelId, @NotNull Long rowVersion) {
        ApplianceModel applianceModel = findActive(applianceModelId);
        checkVersion(applianceModel, rowVersion);
        applianceModel.archive(Instant.now());
        applianceModelRepository.flush();
        return toResponse(applianceModel);
    }

    /**
     * Searches the appliance model catalog.
     *
     * @param applianceModelType the type to filter on, or null for all types
     * @param brand              part of the brand name, or null/blank for no filter
     * @param modelNo            part of model number, or null/blank for no filter
     * @param page               the zero-based page index
     * @param size               the page size ,limit to {@value PageLimits#MAX_PAGE_SIZE}
     * @return the requested page of models
     */
    public PageResponse<ApplianceModelResponse> pageList(ApplianceModelType applianceModelType, String brand, String modelNo, int page, int size) {

        // The sort order is fixed on the server, so the client cannot sort by an arbitrary property.
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, PageLimits.MAX_PAGE_SIZE),
                Sort.by("applianceType", "brand", "modelNo"));
        return PageResponse.of(
                applianceModelRepository.findByFilters(applianceModelType.name(), normalize(brand), normalize(modelNo), pageable)
                        .map(this::toResponse));
    }

    /**
     * Searches models by applianceModelType, with optional brand and model number.
     *
     * @param applianceModelType the type of the appliance model to filter on
     * @param brand              part of the brand name, or null/blank for no filter
     * @param modelNo            part of the model number, or null/blank for no filter
     * @return the requested list of models
     */
    public List<ApplianceModelResponse> allList(@NotNull ApplianceModelType applianceModelType, String brand, String modelNo) {

        return applianceModelRepository.findAllByFilters(applianceModelType.name(), normalize(brand), normalize(modelNo),
                Sort.by("applianceType", "brand", "modelNo")).stream().map(this::toResponse).toList();
    }

    /**
     * Returns the details of an appliance model
     *
     * @param applianceModelId the id of the appliance model
     * @return the matching model with the user who created and last updated it
     * @throws ApplicationException with {@code NOT_FOUND} if no active model has this id
     */
    public ApplianceModelDetailResponse detail(@NotNull UUID applianceModelId) {

        ApplianceModel applianceModel = findActive(applianceModelId);
        Map<UUID, UserBriefResponse> userBriefResponseMap = userService.findByUserIds(List.of(applianceModel.getCreatedBy(), applianceModel.getUpdatedBy()));

        return new ApplianceModelDetailResponse(applianceModelId, applianceModel.getApplianceType(), applianceModel.getBrand(),
                applianceModel.getModelNo(), applianceModel.getRowVersion(), applianceModel.getCreatedAt(), applianceModel.getUpdatedAt(),
                userBriefResponseMap.get(applianceModel.getCreatedBy()), userBriefResponseMap.get(applianceModel.getUpdatedBy()));
    }


    private ApplianceModel findActive(@NotNull UUID applianceModelId) {
        return applianceModelRepository.findById(applianceModelId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.NOT_FOUND, "Appliance model " + applianceModelId + " not found."));
    }

    private void checkVersion(@NotNull ApplianceModel applianceModel, @NotNull Long rowVersion) {
        if (!Objects.equals(applianceModel.getRowVersion(), rowVersion)) {
            throw new ApplicationException(ErrorCode.DUPLICATE_RESOURCE, "The appliance has already been modified by someone else.", false, rowVersion);
        }
    }


    private ApplianceModelResponse toResponse(ApplianceModel model) {
        return new ApplianceModelResponse(model.getApplianceModelId(), model.getApplianceType(),
                model.getBrand(), model.getModelNo(), model.getRowVersion());
    }
}
