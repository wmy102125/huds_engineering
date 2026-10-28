package com.hugo.huds.address.service;

import com.hugo.huds.address.dto.ServiceAddressDetailResponse;
import com.hugo.huds.address.dto.ServiceAddressRequest;
import com.hugo.huds.address.dto.ServiceAddressResponse;
import com.hugo.huds.address.entity.ServiceAddress;
import com.hugo.huds.address.repository.ServiceAddressRepository;
import com.hugo.huds.audit.dto.AuditAction;
import com.hugo.huds.audit.service.AuditService;
import com.hugo.huds.audit.service.ChangedFields;
import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.common.util.ParamNomalize;
import com.hugo.huds.customer.dto.CustomerDetailResponse;
import com.hugo.huds.customer.repository.CustomerRepository;
import com.hugo.huds.sync.dto.SyncPullResponse;
import com.hugo.huds.user.reponse.UserBriefResponse;
import com.hugo.huds.user.service.UserService;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;
import java.util.*;

/**
 * Business operations for customer's service address.
 *
 * @author Meiyan Wang
 * @date 2026-10-19 4:34 p.m.
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

    @Transactional
    public ServiceAddressResponse add(@NotNull UUID serviceAddressId, @NotNull ServiceAddressRequest addressRequest) {
        // A retried request with the same id: the address already exists, so return it as is.
        var existing = serviceAddressRepository.findById(serviceAddressId);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        UUID customerId = addressRequest.customerId();
        ensureCustomerExists(customerId);

        // Normalize first, so that the stored values are exactly what the duplicate check compares.
        String addressLine1 = addressRequest.addressLine1().trim();
        String addressLine2 = ParamNomalize.normalize(addressRequest.addressLine2());
        String city = addressRequest.city().trim();
        String province = addressRequest.province().trim();
        String postalCode = ParamNomalize.normalize(addressRequest.postalCode());
        String countryCode = addressRequest.countryCode().trim();

        // The customer must not already have an identical address.
        Optional<UUID> duplicateId = serviceAddressRepository.findDuplicateId(
                customerId, addressLine1, addressLine2, city, province, postalCode, countryCode);
        if (duplicateId.isPresent()) {
            throw ApplicationException.duplicate(
                    "The customer already has this service address.", duplicateId.get());
        }

        // The first address is always the default. Later ones are the default only when the caller asks for it.
        List<ServiceAddress> existingAddresses = serviceAddressRepository.findByCustomerId(customerId);
        boolean makeDefault = existingAddresses.isEmpty() || Boolean.TRUE.equals(addressRequest.isDefault());
        if (makeDefault) {
            // Clear the old default first, so that the one-default-per-customer unique index is not violated.
            clearPreviousDefault(customerId, serviceAddressId);
        }
        ServiceAddress serviceAddress = new ServiceAddress();
        BeanUtils.copyProperties(addressRequest, serviceAddress);
        serviceAddress.setServiceAddressId(serviceAddressId);
        serviceAddress.setAddressLine1(addressLine1);
        serviceAddress.setAddressLine2(addressLine2);
        serviceAddress.setCity(city);
        serviceAddress.setProvince(province);
        serviceAddress.setPostalCode(postalCode);
        serviceAddress.setCountryCode(countryCode);
        serviceAddress.setIsDefault(makeDefault);
        // saveAndFlush so that the row version and audit timestamps are populated before building the response.
        serviceAddressRepository.saveAndFlush(serviceAddress);
        auditService.recordInTransaction(AuditAction.CREATE, AUDIT_ENTITY_TYPE, serviceAddressId, Map.of());
        return toResponse(serviceAddress);
    }

    @Transactional
    public ServiceAddressResponse update(@NotNull UUID serviceAddressId, @NotNull Long rowVersion, @NotNull ServiceAddressRequest addressRequest) {

        ServiceAddress serviceAddress = findActive(serviceAddressId);
        checkVersion(serviceAddress, rowVersion);

        // An address belongs to one customer for its whole life. To move it, archive it and create a new one.
        if (!Objects.equals(serviceAddress.getCustomerId(), addressRequest.customerId())) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD,
                    "customerId does not match the customer who owns this service address. "
                            + "A service address cannot be moved to another customer.");
        }

        String addressLine1 = addressRequest.addressLine1().trim();
        String addressLine2 = ParamNomalize.normalize(addressRequest.addressLine2());
        String city = addressRequest.city().trim();
        String province = addressRequest.province().trim();
        String postalCode = ParamNomalize.normalize(addressRequest.postalCode());
        String countryCode = addressRequest.countryCode().trim();

        // The customer must not end up with two identical addresses.
        Optional<UUID> duplicateId = serviceAddressRepository.findDuplicateIdExcluding(
                serviceAddress.getCustomerId(), addressLine1, addressLine2, city, province, postalCode, countryCode, serviceAddressId);
        if (duplicateId.isPresent()) {
            throw ApplicationException.duplicate(
                    "The customer already has this service address.", duplicateId.get());
        }

        // null means "leave the default flag as it is".
        boolean currentDefault = Boolean.TRUE.equals(serviceAddress.getIsDefault());
        boolean newDefault = addressRequest.isDefault() == null ? currentDefault : addressRequest.isDefault();
        if (currentDefault && !newDefault) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD,
                    "A customer must keep a default address. Make another address the default instead.");
        }

        // Compare before applying the change. Only the field names go to the audit trail.
        ChangedFields changes = new ChangedFields()
                .track("addressLine1", serviceAddress.getAddressLine1(), addressLine1)
                .track("addressLine2", serviceAddress.getAddressLine2(), addressLine2)
                .track("city", serviceAddress.getCity(), city)
                .track("province", serviceAddress.getProvince(), province)
                .track("postalCode", serviceAddress.getPostalCode(), postalCode)
                .track("countryCode", serviceAddress.getCountryCode(), countryCode)
                .track("isDefault", currentDefault, newDefault);

        if (newDefault && !currentDefault) {
            // Clear the old default first, so that the one-default-per-customer unique index is not violated.
            clearPreviousDefault(serviceAddress.getCustomerId(), serviceAddressId);
        }
        serviceAddress.setAddressLine1(addressLine1);
        serviceAddress.setAddressLine2(addressLine2);
        serviceAddress.setCity(city);
        serviceAddress.setProvince(province);
        serviceAddress.setPostalCode(postalCode);
        serviceAddress.setCountryCode(countryCode);
        serviceAddress.setIsDefault(newDefault);

        // Flush so that the row_version and update_at timestamp are new when the response is built.
        serviceAddressRepository.flush();
        auditService.recordInTransaction(AuditAction.UPDATE, AUDIT_ENTITY_TYPE, serviceAddressId, Map.of("changedFields", changes.names()));
        return toResponse(serviceAddress);
    }

    /**
     * Changes the customer's default address
     *
     * @param serviceAddressId the id of the service address
     * @param rowVersion       the version of the address caller based change on
     * @param wantDefault      the default value need to be changed
     * @return the updated address
     */
    @Transactional
    public ServiceAddressResponse updateDefaultAddress(@NotNull UUID serviceAddressId, @NotNull Long rowVersion, Boolean wantDefault) {
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
        auditService.recordInTransaction(AuditAction.UPDATE, AUDIT_ENTITY_TYPE, serviceAddressId, Map.of("changedFields", List.of("isDefault")));

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
        // An archived address must not keep the default flag, or it could clash with the customer's next default.
        serviceAddress.setIsDefault(false);
        serviceAddress.archive(Instant.now());
        serviceAddressRepository.flush();
        auditService.recordInTransaction(AuditAction.ARCHIVE, AUDIT_ENTITY_TYPE, serviceAddressId, Map.of());
        return toResponse(serviceAddress);
    }

    /**
     * Archives every active service address of a customer. Used when the customer itself is archived,
     * so the caller does not send a row version for each address.
     *
     * @param customerId the id of the customer being archived
     */
    @Transactional
    public void archiveAllOfCustomer(@NotNull UUID customerId) {
        Instant now = Instant.now();
        for (ServiceAddress serviceAddress : serviceAddressRepository.findByCustomerId(customerId)) {
            serviceAddress.setIsDefault(false);
            serviceAddress.archive(now);
            serviceAddressRepository.flush();
            auditService.recordInTransaction(AuditAction.ARCHIVE, AUDIT_ENTITY_TYPE,
                    serviceAddress.getServiceAddressId(), Map.of());
        }
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
     * Searches the change rows by since, sinceId and limit.
     *
     * @param since   the change rows start time
     * @param sinceId the change rows start id
     * @param limit   this search batch max size number
     * @return matching rows
     */
    public SyncPullResponse fetchChanges(Instant since, UUID sinceId, int limit) {

        List<ServiceAddressRepository.ChangeRow> rows = serviceAddressRepository.findChanges(since, sinceId, limit + 1);
        // There is no change row, return default response
        if (CollectionUtils.isEmpty(rows)) {
            return SyncPullResponse.nullListResponse(since, sinceId);
        }

        boolean hasMore = rows.size() > limit;
        List<ServiceAddressRepository.ChangeRow> page = hasMore ? rows.subList(0, limit) : rows;

        List<ServiceAddressDetailResponse> detailResponses = page.stream().map(changeRow -> {
            return new ServiceAddressDetailResponse(changeRow.getServiceAddressId(), changeRow.getCustomerId(),
                    changeRow.getAddressLine1(),
                    changeRow.getAddressLine2(),
                    changeRow.getCity(),
                    changeRow.getProvince(),
                    changeRow.getPostalCode(),
                    changeRow.getCountryCode(),
                    changeRow.getIsDefault(),
                    changeRow.getRowVersion(),
                    changeRow.getCreatedAt(),
                    changeRow.getUpdatedAt(),
                    changeRow.getCreator(),
                    changeRow.getEditor(),
                    Boolean.TRUE.equals(changeRow.getArchived()));
        }).toList();
        ServiceAddressRepository.ChangeRow last = page.get(page.size() - 1);
        Instant nextSince = last.getUpdatedAt();
        UUID nextSinceId = last.getServiceAddressId();
        return new SyncPullResponse(detailResponses, nextSince, nextSinceId, hasMore);
    }

    /**
     * Looks up for the serv
     *
     * @param serviceAddressId
     * @return
     */
    public ServiceAddressDetailResponse detail(@NotNull UUID serviceAddressId) {

        ServiceAddress serviceAddress = findActive(serviceAddressId);
        Set<UUID> userIds = new HashSet<>();
        if (serviceAddress.getCreatedBy() != null) {
            userIds.add(serviceAddress.getCreatedBy());
        }
        if (serviceAddress.getUpdatedBy() != null) {
            userIds.add(serviceAddress.getUpdatedBy());
        }
        Map<UUID, UserBriefResponse> userBriefResponseMap = userService.findByUserIds(userIds);

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
                userBriefResponseMap.get(serviceAddress.getUpdatedBy()),
                serviceAddress.getArchivedAt() != null);
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

    /**
     * Clears the customer's current default address, if it is not the given one.
     * The change is flushed right away so that the unique index on the default flag is not violated
     * when the caller then marks another address as the default.
     *
     * @param customerId the id of the customer
     * @param exceptId   the id of the address that is about to become the default
     */
    private void clearPreviousDefault(UUID customerId, UUID exceptId) {
        serviceAddressRepository.findByCustomerIdAndIsDefault(customerId, true)
                .filter(previous -> !previous.getServiceAddressId().equals(exceptId))
                .ifPresent(previous -> {
                    previous.setIsDefault(false);
                    serviceAddressRepository.flush();
                    auditService.recordInTransaction(AuditAction.UPDATE, AUDIT_ENTITY_TYPE,
                            previous.getServiceAddressId(), Map.of("changedFields", List.of("isDefault")));
                });
    }

    private ServiceAddressResponse toResponse(ServiceAddress serviceAddress) {
        return new ServiceAddressResponse(serviceAddress.getServiceAddressId(), serviceAddress.getCustomerId(), serviceAddress.getAddressLine1(), serviceAddress.getAddressLine2(), serviceAddress.getCity(), serviceAddress.getProvince(), serviceAddress.getPostalCode(), serviceAddress.getCountryCode(), serviceAddress.getIsDefault(), serviceAddress.getRowVersion());
    }

}