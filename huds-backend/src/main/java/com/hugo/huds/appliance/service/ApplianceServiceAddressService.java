package com.hugo.huds.appliance.service;

import com.hugo.huds.address.entity.ServiceAddress;
import com.hugo.huds.address.repository.ServiceAddressRepository;
import com.hugo.huds.appliance.dto.*;
import com.hugo.huds.appliance.entity.Appliance;
import com.hugo.huds.appliance.entity.ApplianceServiceAddress;
import com.hugo.huds.appliance.repository.ApplianceRepository;
import com.hugo.huds.appliance.repository.ApplianceServiceAddressRepository;
import com.hugo.huds.audit.dto.AuditAction;
import com.hugo.huds.audit.service.AuditService;
import com.hugo.huds.audit.service.ChangedFields;
import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.user.service.UserService;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * @author Meiyan Wang
 * @date 2026-10-24 10:19 a.m.
 */
@Validated
@Service
@Slf4j
@Transactional(readOnly = true)
public class ApplianceServiceAddressService {

    private final ApplianceServiceAddressRepository applianceServiceAddressRepository;
    private final ServiceAddressRepository serviceAddressRepository;
    private final ApplianceRepository applianceRepository;
    private final AuditService auditService;
    private final UserService userService;

    // Do not use the SyncEntityType.APPLIANCE_SERVICE_ADDRESS, It will lead to circular dependencies.
    private static final String AUDIT_ENTITY_TYPE = "APPLIANCE_SERVICE_ADDRESS";

    public ApplianceServiceAddressService(ApplianceServiceAddressRepository applianceServiceAddressRepository, ServiceAddressRepository serviceAddressRepository, ApplianceRepository applianceRepository, AuditService auditService, UserService userService) {
        this.applianceServiceAddressRepository = applianceServiceAddressRepository;
        this.serviceAddressRepository = serviceAddressRepository;
        this.applianceRepository = applianceRepository;
        this.auditService = auditService;
        this.userService = userService;
    }

    /**
     * Creates an appliance with the id chosen by the caller (the client generated it offline).
     *
     * @param applianceServiceAddressId appliance and service address relationship id
     * @param request                   the appliance service address relationship details
     * @return the created appliance service address relationship
     */
    @Transactional
    public ApplianceServiceAddressResponse add(@NotNull UUID applianceServiceAddressId, @NotNull ApplianceServiceAddressRequest request) {

        // Whether appliance exists
        UUID applianceId = request.applianceId();
        applianceExists(applianceId);

        // Whether service address exists
        UUID serviceAddressId = request.serviceAddressId();
        serviceAddressExists(serviceAddressId);

        // There cannot be identical appliance service address
        Optional<ApplianceServiceAddress> applianceOptional = applianceServiceAddressRepository.findByApplianceIdAndServiceAddressId(applianceId, serviceAddressId);
        if (applianceOptional.isPresent()) {
            return toResponse(applianceOptional.get());
        }

        // add a new appliance model
        ApplianceServiceAddress applianceServiceAddress = new ApplianceServiceAddress();
        applianceServiceAddress.setApplianceServiceAddressId(applianceServiceAddressId);
        applianceServiceAddress.setApplianceId(applianceId);
        applianceServiceAddress.setServiceAddressId(serviceAddressId);
        applianceServiceAddress.setNote(request.note());
        // saveAndFlush so that the row_version and update_at timestamp are populated before building the response.
        applianceServiceAddressRepository.saveAndFlush(applianceServiceAddress);
        auditService.recordInTransaction(AuditAction.CREATE, AUDIT_ENTITY_TYPE, applianceServiceAddressId, Map.of());
        return toResponse(applianceServiceAddress);
    }

    /**
     * Updates an appliance service address relationship ,provided the caller has seen the current version.
     *
     * @param applianceServiceAddressId appliance and service address relationship id
     * @param request                   the appliance service address relationship details
     * @return the updated appliance service address relationship
     */
    @Transactional
    public ApplianceServiceAddressResponse update(@NotNull UUID applianceServiceAddressId,@NotNull Long rowVersion, @NotNull ApplianceServiceAddressRequest request) {
        // Whether appliance exists
        UUID applianceId = request.applianceId();
        applianceExists(applianceId);

        // Whether service address exists
        UUID serviceAddressId = request.serviceAddressId();
        serviceAddressExists(serviceAddressId);

        // Whether the appliance service address relationship exists
        ApplianceServiceAddress applianceServiceAddress = findActive(applianceServiceAddressId);

        checkVersion(applianceServiceAddress, rowVersion);

        // Compare before applying the change. Only the field names go to audit trail.
        ChangedFields changes = new ChangedFields()
                .track("note", applianceServiceAddress.getNote(), request.note())
                .track("serviceAddressId", applianceServiceAddress.getServiceAddressId(), request.serviceAddressId());

        applianceServiceAddress.setServiceAddressId(serviceAddressId);
        // Flush so that the row_version and update_at timestamp are new when the response is built.
        applianceRepository.flush();
        auditService.recordInTransaction(AuditAction.UPDATE, AUDIT_ENTITY_TYPE, applianceId, Map.of("changedFields", changes.names()));

        return toResponse(applianceServiceAddress);
    }

    /**
     * Soft-deletes appliance service address relationship, Archive relationships are invisible to every entity query
     * (see @SQLRestriction at ApplianceServiceAddress), so the second call for the same id reports "not found"
     *
     * @param applianceServiceAddressId appliance and service address relationship id
     * @param rowVersion  the version on which the caller based on change
     */
    @Transactional
    public ApplianceServiceAddressResponse archive(@NotNull UUID applianceServiceAddressId, @NotNull Long rowVersion) {
        ApplianceServiceAddress applianceServiceAddress = findActive(applianceServiceAddressId);
        checkVersion(applianceServiceAddress, rowVersion);
        applianceServiceAddress.archive(Instant.now());
        applianceRepository.flush();
        return toResponse(applianceServiceAddress);
    }

    private void applianceExists(@NotNull UUID applianceId) {
        if (!applianceRepository.existsById(applianceId)) {
            throw new ApplicationException(ErrorCode.NOT_FOUND, "Appliance " + applianceId + " not found.");
        }
    }

    private Appliance findActiveAppliance(@NotNull UUID applianceId) {
        return applianceRepository.findById(applianceId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.NOT_FOUND, "Appliance " + applianceId + " not found."));
    }

    private void serviceAddressExists(@NotNull UUID serviceAddressId) {
        if (!serviceAddressRepository.existsById(serviceAddressId)) {
            throw new ApplicationException(ErrorCode.NOT_FOUND, "Service address: " + serviceAddressId + " not found.");
        }
    }

    private ServiceAddress findActiveAddress(@NotNull UUID serviceAddressId) {
        return serviceAddressRepository.findById(serviceAddressId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.NOT_FOUND, "Service address: " + serviceAddressId + " not found."));
    }

    private ApplianceServiceAddress findActive(@NotNull UUID applianceServiceAddressId) {
        return applianceServiceAddressRepository.findById(applianceServiceAddressId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.NOT_FOUND, "Appliance service address: " + applianceServiceAddressId + " not found."));
    }

    // Optimistic concurrency: the caller must have seen the current version
    private void checkVersion(@NotNull ApplianceServiceAddress serviceAddress, @NotNull Long rowVersion) {
        if (!Objects.equals(serviceAddress.getRowVersion(), rowVersion)) {
            throw new ApplicationException(ErrorCode.DUPLICATE_RESOURCE, "The appliance has already been modified by someone else.", false, serviceAddress.getRowVersion());
        }
    }


    private ApplianceServiceAddressResponse toResponse(ApplianceServiceAddress serviceAddress) {
        return new ApplianceServiceAddressResponse(
                serviceAddress.getApplianceId(),
                serviceAddress.getServiceAddressId(),
                serviceAddress.getNote(),
                serviceAddress.getRowVersion());

    }
}
