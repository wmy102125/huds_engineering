package com.hugo.huds.address.service;

import com.hugo.huds.address.dto.ServiceAddressDetailResponse;
import com.hugo.huds.address.dto.ServiceAddressResponse;
import com.hugo.huds.address.dto.ServiceAddressRequest;
import com.hugo.huds.address.entity.ServiceAddress;
import com.hugo.huds.address.repository.ServiceAddressRepository;
import com.hugo.huds.audit.dto.AuditAction;
import com.hugo.huds.audit.service.AuditService;
import com.hugo.huds.audit.service.ChangedFields;
import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.common.util.ParamNomalize;
import com.hugo.huds.customer.repository.CustomerRepository;
import com.hugo.huds.user.reponse.UserBriefResponse;
import com.hugo.huds.user.service.UserService;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Business operations for customer's service address.
 *
 * @author Meiyan Wang
 * @date 2026-10-19 4:34 p.m.
 */
@Validated
@Service
@Slf4j
@Transactional(readOnly = true)
public class ServiceAddressService {

    private static final String AUDIT_ENTITY_TYPE = "SERVICE_ADDRESS";

    private final CustomerRepository customerRepository;
    private final ServiceAddressRepository serviceAddressRepository;
    private final AuditService auditService;
    private final UserService userService;

    public ServiceAddressService(CustomerRepository customerRepository, ServiceAddressRepository serviceAddressRepository, AuditService auditService, UserService userService) {
        this.customerRepository = customerRepository;
        this.serviceAddressRepository = serviceAddressRepository;
        this.auditService = auditService;
        this.userService = userService;
    }

    /**
     * Creates a service address with the id chosen by the caller (the client generates it offline).
     * The customer's first address becomes the default address.
     *
     * @param serviceAddressId the id of the new address
     * @param addressRequest   the address details
     * @return the created address
     */
    @Transactional
    public ServiceAddressResponse add(@NotNull UUID serviceAddressId, @NotNull ServiceAddressRequest addressRequest) {

        UUID customerId = addressRequest.customerId();
        ensureCustomerExists(customerId);

        // The customer must not already have an identical address.
        List<ServiceAddress> existingAddresses = serviceAddressRepository.findByCustomerId(customerId);
        boolean duplicate = existingAddresses.stream().anyMatch(address ->
                Objects.equals(address.getAddressLine1(), addressRequest.addressLine1())
                        && Objects.equals(address.getAddressLine2(), addressRequest.addressLine2())
                        && Objects.equals(address.getCity(), addressRequest.city())
                        && Objects.equals(address.getProvince(), addressRequest.province())
                        && Objects.equals(address.getPostalCode(), addressRequest.postalCode())
                        && Objects.equals(address.getCountryCode(), addressRequest.countryCode()));
        if (duplicate) {
            throw new ApplicationException(ErrorCode.DUPLICATE_RESOURCE,
                    "Customer " + customerId + " already has this service address.");
        }

        ServiceAddress serviceAddress = new ServiceAddress();
        BeanUtils.copyProperties(addressRequest, serviceAddress);
        serviceAddress.setServiceAddressId(serviceAddressId);
        // A customer has at most one active default address, so only the first address becomes the default.
        serviceAddress.setIsDefault(existingAddresses.isEmpty());
        // saveAndFlush so that the row version and audit timestamps are populated before building the response.
        serviceAddressRepository.saveAndFlush(serviceAddress);
        auditService.recordInTransaction(AuditAction.CREATE, AUDIT_ENTITY_TYPE, serviceAddressId, Map.of());
        return toResponse(serviceAddress);
    }

    /**
     * Updates a service address, provided the caller has seen the current version.
     *
     * @param serviceAddressId the id of the service address
     * @param rowVersion       the version of the address the caller based change on
     * @param addressRequest   the updated address details
     * @return the updated address
     */
    @Transactional
    public ServiceAddressResponse update(@NotNull UUID serviceAddressId, @NotNull Long rowVersion, @NotNull ServiceAddressRequest addressRequest) {
        UUID customerId = addressRequest.customerId();
        ensureCustomerExists(customerId);

        // The customer must not end up with two identical addresses.
        if (serviceAddressRepository.existsDuplicateExcluding(
                customerId,
                addressRequest.addressLine1().trim(),
                ParamNomalize.normalize(addressRequest.addressLine2()),
                addressRequest.city().trim(),
                addressRequest.province().trim(),
                ParamNomalize.normalize(addressRequest.postalCode()),
                addressRequest.countryCode().trim(),
                serviceAddressId)) {
            throw new ApplicationException(ErrorCode.DUPLICATE_RESOURCE,
                    "Customer " + addressRequest.customerId() + " already has this service address.");

        }
        ServiceAddress serviceAddress = findActive(serviceAddressId);
        checkVersion(serviceAddress, rowVersion);

        // Compare before applying the change. Only the field names go to the audit trail.
        ChangedFields changes = new ChangedFields()
                .track("addressLine1", serviceAddress.getAddressLine1(), addressRequest.addressLine1())
                .track("addressLine2", serviceAddress.getAddressLine2(), addressRequest.addressLine2())
                .track("city", serviceAddress.getCity(), addressRequest.city())
                .track("province", serviceAddress.getProvince(), addressRequest.province())
                .track("postalCode", serviceAddress.getPostalCode(), addressRequest.postalCode())
                .track("countryCode", serviceAddress.getCountryCode(), addressRequest.countryCode());

        serviceAddress.setAddressLine1(addressRequest.addressLine1().trim());
        serviceAddress.setAddressLine2(addressRequest.addressLine2());
        serviceAddress.setCity(addressRequest.city().trim());
        serviceAddress.setProvince(addressRequest.province().trim());
        serviceAddress.setPostalCode(addressRequest.postalCode());
        serviceAddress.setCountryCode(addressRequest.countryCode().trim());

        // Flush so that the row_version and update_at timestamp are new  when the response is built.
        serviceAddressRepository.flush();
        auditService.recordInTransaction(AuditAction.UPDATE, AUDIT_ENTITY_TYPE, serviceAddressId, Map.of("changedFields", changes.names()));
        return toResponse(serviceAddress);
    }

    /**
     * Changes the customer's default address
     * @param serviceAddressId the id of the service address
     * @param rowVersion the version of the address caller based change on
     * @param wantDefault the default value need to be changed
     * @return the updated address
     */
    @Transactional
    public ServiceAddressResponse changeDefaultAddress(@NotNull UUID serviceAddressId, @NotNull Long rowVersion, Boolean wantDefault) {
        if (serviceAddressId == null || rowVersion == null || wantDefault == null) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD,
                    "Service address id, row version and default flag are required.");
        }
        ServiceAddress serviceAddress = findActive(serviceAddressId);
        checkVersion(serviceAddress, rowVersion);
        boolean currentDefault = Boolean.TRUE.equals(serviceAddress.getIsDefault());
        if (currentDefault == wantDefault) {
            // Nothing to change.
            return toResponse(serviceAddress);
        }
        if (!wantDefault) {
            // The address is the current default and the caller wants to unset it.
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD,
                    "A customer must keep a default address. Make another address the default instead.");
        }

        // Clear the previous default (if any) and flush it first, so the unique index is not violated.
        serviceAddressRepository.findByCustomerIdAndIsDefault(serviceAddress.getCustomerId(), true)
                .ifPresent(previous -> {
                    previous.setIsDefault(false);
                    serviceAddressRepository.flush();
                    auditService.recordInTransaction(AuditAction.UPDATE, AUDIT_ENTITY_TYPE,
                            previous.getServiceAddressId(), Map.of("changedFields", List.of("isDefault")));
                });

        serviceAddress.setIsDefault(true);
        serviceAddressRepository.flush();
        auditService.recordInTransaction(AuditAction.UPDATE, AUDIT_ENTITY_TYPE, serviceAddressId, Map.of("changedFields",List.of("isDefault")));

        return toResponse(serviceAddress);
    }


    /**
     * Soft-deletes a service address, Archived addresses are invisible to every entity query
     * (see {@code @SQLRestriction} at {@link ServiceAddress}), so the second call for the same id reports "not found"
     *
     * @param serviceAddressId the id of the address to archive
     * @param rowVersion       the version of the address the caller based the change on
     * @return the archived address
     */
    @Transactional
    public ServiceAddressResponse archive(@NotNull UUID serviceAddressId, @NotNull Long rowVersion) {
        ServiceAddress serviceAddress = findActive(serviceAddressId);
        checkVersion(serviceAddress, rowVersion);
        serviceAddress.archive(Instant.now());
        serviceAddressRepository.flush();
        auditService.recordInTransaction(AuditAction.ARCHIVE, AUDIT_ENTITY_TYPE, serviceAddressId, Map.of());
        return toResponse(serviceAddress);
    }

    /**
     * Lists a customer's service addresses.
     *
     * @param customerId the id of the customer
     * @return the customer's active service addresses
     */
    public List<ServiceAddressResponse> list(@NotNull UUID customerId) {

        return serviceAddressRepository.findByCustomerId(customerId).stream().map(this::toResponse).toList();
    }


    /**
     * Looks up for the serv
     *
     * @param serviceAddressId
     * @return
     */
    public ServiceAddressDetailResponse detail(@NotNull UUID serviceAddressId) {

        ServiceAddress serviceAddress = findActive(serviceAddressId);
        Map<UUID, UserBriefResponse> userBriefResponseMap = userService.findByUserIds(List.of(serviceAddress.getCreatedBy(), serviceAddress.getUpdatedBy()));

        return new ServiceAddressDetailResponse(serviceAddress.getServiceAddressId(),
                serviceAddress.getCustomerId(),
                serviceAddress.getAddressLine1(),
                serviceAddress.getAddressLine2(),
                serviceAddress.getCity(),
                serviceAddress.getProvince(),
                serviceAddress.getPostalCode(),
                serviceAddress.getCountryCode(),
                serviceAddress.getIsDefault(),
                serviceAddress.getRowVersion(),
                serviceAddress.getCreatedAt(),
                serviceAddress.getUpdatedAt(),
                userBriefResponseMap.get(serviceAddress.getCreatedBy()),
                userBriefResponseMap.get(serviceAddress.getUpdatedBy()));
    }

    private void ensureCustomerExists(@NotNull UUID customerId) {
        if (!customerRepository.existsById(customerId)) {
            throw new ApplicationException(ErrorCode.NOT_FOUND, "Customer " + customerId + " not found");
        }
    }

    private ServiceAddress findActive(@NotNull UUID serviceAddressId) {
        return serviceAddressRepository.findById(serviceAddressId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.NOT_FOUND, "serviceAddressId " + serviceAddressId + " not found"));
    }

    // Optimistic concurrency: the caller must have seen the current version
    private void checkVersion(@NotNull ServiceAddress serviceAddress, @NotNull Long rowVersion) {
        if (!Objects.equals(serviceAddress.getRowVersion(), rowVersion)) {
            throw new ApplicationException(ErrorCode.VERSION_CONFLICT, "The service address has already been modified by someone else.", false, serviceAddress.getRowVersion());
        }
    }

    private ServiceAddressResponse toResponse(ServiceAddress serviceAddress) {
        return new ServiceAddressResponse(serviceAddress.getServiceAddressId(), serviceAddress.getCustomerId(), serviceAddress.getAddressLine1(), serviceAddress.getAddressLine2(), serviceAddress.getCity(), serviceAddress.getProvince(), serviceAddress.getPostalCode(), serviceAddress.getCountryCode(), serviceAddress.getIsDefault(), serviceAddress.getRowVersion());
    }

}
