package com.hugo.huds.appliance.service;

import com.hugo.huds.appliance.dto.*;
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
public class ApplianceModelService {

    private final ApplianceModelRepository applianceModelRepository;
    private final ApplianceRepository applianceRepository;
    private final AuditService auditService;
    private final UserService userService;
    // Do not use the SyncEntityType.APPLIANCE_MODEL, It will lead to circular dependencies.
    private static final String AUDIT_ENTITY_TYPE = "APPLIANCE_MODEL";

    public ApplianceModelService(ApplianceModelRepository applianceModelRepository, ApplianceRepository applianceRepository, AuditService auditService, UserService userService) {
        this.applianceModelRepository = applianceModelRepository;
        this.applianceRepository = applianceRepository;
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

        // A retried request with the same id: the model already exists, so return it as is.
        var existing = applianceModelRepository.findById(applianceModelId);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        String brand = normalize(modelRequest.brand());
        String modelNo = normalize(modelRequest.modelNo());
        Optional<UUID> existsApplianceModelIdOptional = applianceModelRepository.findDuplicateId(modelRequest.applianceType(), brand, modelNo);

        if (existsApplianceModelIdOptional.isPresent()) {
            throw ApplicationException.duplicate(
                    "An appliance model with the same applianceType, brand and modelNo already exists.",
                    existsApplianceModelIdOptional.get());
        }

        // add a new appliance model
        ApplianceModel applianceModel = new ApplianceModel();
        applianceModel.setApplianceType(modelRequest.applianceType());
        applianceModel.setBrand(brand);
        applianceModel.setModelNo(modelNo);
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
        checkVersion(applianceModel, rowVersion);

        String brand = normalize(modelRequest.brand());
        String modelNo = normalize(modelRequest.modelNo());
        Optional<UUID> duplicateId = applianceModelRepository.findDuplicateIdExcluding(
                modelRequest.applianceType(), brand, modelNo, applianceModelId);
        if (duplicateId.isPresent()) {
            throw ApplicationException.duplicate(
                    "An appliance model with the same applianceType, brand and modelNo already exists.",
                    duplicateId.get());
        }


        // Compare before applying the change. Only the field names go to audit trail.
        ChangedFields changes = new ChangedFields()
                .track("applianceType", applianceModel.getApplianceType(), modelRequest.applianceType())
                .track("brand", applianceModel.getBrand(), brand)
                .track("modelNo", applianceModel.getModelNo(), modelNo);

        applianceModel.setApplianceType(modelRequest.applianceType());
        applianceModel.setBrand(brand);
        applianceModel.setModelNo(modelNo);
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
    @Transactional
    public ApplianceModelResponse archive(@NotNull UUID applianceModelId, @NotNull Long rowVersion) {
        ApplianceModel model = findActive(applianceModelId);
        checkVersion(model, rowVersion);

        if (applianceRepository.existsByApplianceModelId(applianceModelId)) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD,
                    "The appliance model is still used by appliances and cannot be archived.");
        }

        model.archive(Instant.now());
        applianceModelRepository.flush();
        auditService.recordInTransaction(AuditAction.ARCHIVE, AUDIT_ENTITY_TYPE, applianceModelId, Map.of());
        return toResponse(model);
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
                applianceModelRepository.findByFilters(applianceModelType == null ? "" : applianceModelType.name(), normalize(brand), normalize(modelNo), pageable)
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
     * Searches the change rows by since, sinceId and limit.
     *
     * @param since   the change rows start time
     * @param sinceId the change rows start id
     * @param limit   this search batch max size number
     * @return matching rows
     */
    public SyncPullResponse fetchChanges(Instant since, UUID sinceId, int limit) {

        List<ApplianceModelRepository.ChangeRow> rows = applianceModelRepository.findChanges(since, sinceId, limit + 1);
        // There is no change row, return default response
        if (CollectionUtils.isEmpty(rows)) {
            return SyncPullResponse.nullListResponse(since, sinceId);
        }

        boolean hasMore = rows.size() > limit;
        List<ApplianceModelRepository.ChangeRow> page = hasMore ? rows.subList(0, limit) : rows;

        List<ApplianceModelDetailResponse> items = page.stream().map(row -> new ApplianceModelDetailResponse(
                row.getApplianceModelId(),
                row.getApplianceType(),
                row.getBrand(),
                row.getModelNo(),
                row.getRowVersion(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                row.getCreator(),
                row.getEditor(),
                Boolean.TRUE.equals(row.getArchived()))).toList();

        ApplianceModelRepository.ChangeRow last = page.get(page.size() - 1);
        return new SyncPullResponse(items, last.getUpdatedAt(), last.getApplianceModelId(), hasMore);
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
        // createdBy / updatedBy can be null (e.g. seed data), and List.of() rejects null elements.
        Set<UUID> userIds = new HashSet<>();
        if (applianceModel.getCreatedBy() != null) {
            userIds.add(applianceModel.getCreatedBy());
        }
        if (applianceModel.getUpdatedBy() != null) {
            userIds.add(applianceModel.getUpdatedBy());
        }
        Map<UUID, UserBriefResponse> userBriefResponseMap = userService.findByUserIds(userIds);

        // Add current applianceModel's appliance list
        List<ApplianceResponse> appliances = applianceRepository.findByApplianceModelId(applianceModelId)
                .stream().<ApplianceResponse>map(appliance -> {
                    return new ApplianceResponse(appliance.getApplianceId(),
                            appliance.getApplianceModelId(),
                            appliance.getSerialNo(),
                            appliance.getSerialVerified(),
                            appliance.getIdentityNotes(),
                            appliance.getRowVersion());
                }).toList();


        return new ApplianceModelDetailResponse(applianceModelId, applianceModel.getApplianceType(), applianceModel.getBrand(),
                applianceModel.getModelNo(), applianceModel.getRowVersion(), applianceModel.getCreatedAt(), applianceModel.getUpdatedAt(),
                userBriefResponseMap.get(applianceModel.getCreatedBy()), userBriefResponseMap.get(applianceModel.getUpdatedBy()), applianceModel.getArchivedAt() != null);
    }


    private ApplianceModel findActive(@NotNull UUID applianceModelId) {
        return applianceModelRepository.findById(applianceModelId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.NOT_FOUND, "Appliance model " + applianceModelId + " not found."));
    }

    // Optimistic concurrency: the caller must have seen the current version
    private void checkVersion(@NotNull ApplianceModel applianceModel, @NotNull Long rowVersion) {
        if (!Objects.equals(applianceModel.getRowVersion(), rowVersion)) {
            throw new ApplicationException(ErrorCode.VERSION_CONFLICT, "The appliance model has already been modified by someone else.", false, applianceModel.getRowVersion());
        }
    }


    private ApplianceModelResponse toResponse(ApplianceModel model) {
        return new ApplianceModelResponse(model.getApplianceModelId(), model.getApplianceType(),
                model.getBrand(), model.getModelNo(), model.getRowVersion());
    }
}
