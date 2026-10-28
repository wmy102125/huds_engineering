package com.hugo.huds.customer.service;

import com.hugo.huds.address.service.ServiceAddressService;
import com.hugo.huds.audit.dto.AuditAction;
import com.hugo.huds.audit.service.AuditService;
import com.hugo.huds.audit.service.ChangedFields;
import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.common.response.ExistsResponse;
import com.hugo.huds.common.response.PageLimits;
import com.hugo.huds.common.response.PageResponse;
import com.hugo.huds.customer.dto.CustomerDetailResponse;
import com.hugo.huds.customer.dto.CustomerRequest;
import com.hugo.huds.customer.dto.CustomerResponse;
import com.hugo.huds.customer.entity.Customer;
import com.hugo.huds.customer.repository.CustomerRepository;
import com.hugo.huds.sync.dto.SyncPullResponse;
import com.hugo.huds.user.reponse.UserBriefResponse;
import com.hugo.huds.user.service.UserService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
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
 * Business operations for customer
 *
 * @author Meiyan Wang
 * @date 2026-10-16 6:14 p.m.
 */
@Validated
@Service
@Slf4j
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final AuditService auditService;
    private final UserService userService;
    private final ServiceAddressService serviceAddressService;
    // Do not use the SyncEntityType.CUSTOMER, It will lead to circular dependencies.
    private static final String AUDIT_ENTITY_TYPE = "CUSTOMER";

    public CustomerService(CustomerRepository customerRepository, AuditService auditService, UserService userService, ServiceAddressService serviceAddressService) {
        this.customerRepository = customerRepository;
        this.auditService = auditService;
        this.userService = userService;
        this.serviceAddressService = serviceAddressService;
    }

    /**
     * Creates a customer with the id chosen by the caller(the client generates it offline)
     *
     * @param customerId the id of the new customer
     * @param request    the customer details
     * @return the created customer
     */
    @Transactional
    public CustomerResponse add(@NotNull UUID customerId, @NotNull CustomerRequest request) {
        // A retried request with the same id: the customer already exists, so return it as is.
        var existing = customerRepository.findById(customerId);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        // The email is optional. When it is provided, it must not belong to another customer.
        String email = request.email();
        if (email != null && !email.isBlank()) {
            Optional<UUID> duplicateId = customerRepository.findIdByEmail(email);
            if (duplicateId.isPresent()) {
                throw ApplicationException.duplicate(
                        "A customer with the same email already exists.", duplicateId.get());
            }
        }

        Customer customer = new Customer();
        BeanUtils.copyProperties(request, customer);
        customer.setCustomerId(customerId);
        // saveAndFlush so that the version and audit timestamps are populated before building the response.
        customerRepository.saveAndFlush(customer);
        auditService.recordInTransaction(AuditAction.CREATE, AUDIT_ENTITY_TYPE, customerId, Map.of());
        return toResponse(customer);
    }


    /**
     * Update the customer only if the client's version matches the current version in the database.
     *
     * @param customerId            the id of the customer to update
     * @param rowVersion            the customer version on which the update is based
     * @param updateCustomerRequest the updated customer detail
     * @return the updated customer
     */
    @Transactional
    public CustomerResponse update(@NotNull UUID customerId, @NotNull Long rowVersion, @NotNull CustomerRequest updateCustomerRequest) {

        Customer customer = findActive(customerId);
        checkVersion(customer, rowVersion);

        // The email is optional. When it is provided, it must not belong to another customer.
        String email = updateCustomerRequest.email();
        if (email != null && !email.isBlank()) {
            Optional<UUID> duplicateId = customerRepository.findIdByEmailExcluding(email, customerId);
            if (duplicateId.isPresent()) {
                throw ApplicationException.duplicate(
                        "Another customer already uses the same email.", duplicateId.get());
            }
        }

        // Compare before applying the change. Only the field names go to the audit trail.
        ChangedFields changedFields = new ChangedFields()
                .track("customerName", customer.getCustomerName(), updateCustomerRequest.customerName())
                .track("email", customer.getEmail(), updateCustomerRequest.email())
                .track("phone", customer.getPhone(), updateCustomerRequest.phone())
                .track("notes", customer.getNotes(), updateCustomerRequest.notes());

        customer.setCustomerName(updateCustomerRequest.customerName());
        customer.setEmail(updateCustomerRequest.email());
        customer.setPhone(updateCustomerRequest.phone());
        customer.setNotes(updateCustomerRequest.notes());

        // Flush so that the row_version and update_at are the new when the response is built.
        customerRepository.flush();
        auditService.recordInTransaction(AuditAction.UPDATE, AUDIT_ENTITY_TYPE, customerId, Map.of("changedFields", changedFields.names()));

        return toResponse(customer);
    }

    /**
     * Checks whether the email address belongs to a customer.
     *
     * @param email the email address to check
     * @return whether a customer with current email has already exists
     */
    public ExistsResponse existsEmail(@NotBlank String email) {
        return new ExistsResponse(customerRepository.existsByEmailIgnoreCase(email));
    }

    public PageResponse<CustomerResponse> pageList(String customerName, String email, String phone, int page, int size) {

        // The sort order is fixed on the server, so the client cannot sort by an arbitrary property.
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, PageLimits.MAX_PAGE_SIZE),
                Sort.by("createdAt", "customerId").descending());
        return PageResponse.of(
                customerRepository.findPageByFilters(normalize(customerName),
                                normalize(email),
                                normalize(phone), pageable)
                        .map(this::toResponse));
    }

    /**
     * List all customers
     *
     * @return the details of all customers
     */
    public List<CustomerResponse> allList(String customerName, String email, String phone) {
        return customerRepository.findByFilters(normalize(customerName),
                        normalize(email),
                        normalize(phone),
                        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("customerId")))
                .stream().map(this::toResponse).toList();
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

        List<CustomerRepository.ChangeRow> rows = customerRepository.findChanges(since, sinceId, limit + 1);
        // There is no change row, return default response
        if (CollectionUtils.isEmpty(rows)) {
            return SyncPullResponse.nullListResponse(since, sinceId);
        }

        boolean hasMore = rows.size() > limit;
        List<CustomerRepository.ChangeRow> page = hasMore ? rows.subList(0, limit) : rows;

        List<CustomerDetailResponse> detailResponses = page.stream().map(changeRow -> {
            return new CustomerDetailResponse(changeRow.getCustomerId(),
                    changeRow.getCustomerName(),
                    changeRow.getEmail(),
                    changeRow.getPhone(),
                    changeRow.getNotes(),
                    changeRow.getRowVersion(),
                    changeRow.getCreatedAt(),
                    changeRow.getUpdatedAt(),
                    changeRow.getCreator(),
                    changeRow.getEditor(),
                    Boolean.TRUE.equals(changeRow.getArchived()));
        }).toList();
        CustomerRepository.ChangeRow last = page.get(page.size() - 1);
        Instant nextSince = last.getUpdatedAt();
        UUID nextSinceId = last.getCustomerId();
        return new SyncPullResponse(detailResponses, nextSince, nextSinceId, hasMore);
    }

    public CustomerDetailResponse detail(@NotNull UUID customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.NOT_FOUND, "Customer: " + customerId + " not found."));

        Set<UUID> userIds = new HashSet<>();
        if (customer.getCreatedBy() != null) {
            userIds.add(customer.getCreatedBy());
        }
        if (customer.getUpdatedBy() != null) {
            userIds.add(customer.getUpdatedBy());
        }
        Map<UUID, UserBriefResponse> userBriefResponseMap = userService.findByUserIds(userIds);

        return new CustomerDetailResponse(customerId,
                customer.getCustomerName(),
                customer.getEmail(),
                customer.getPhone(),
                customer.getNotes(),
                customer.getRowVersion(),
                customer.getCreatedAt(),
                customer.getUpdatedAt(),
                userBriefResponseMap.get(customer.getCreatedBy()),
                userBriefResponseMap.get(customer.getUpdatedBy()),
                customer.getArchivedAt() != null);
    }

    /**
     * Soft-deletes a customer. Archived customers are invisible to every entity query
     * (see{@code  @SQLRestriction} on {@link Customer}) so a second call for the same id reports "not found"
     *
     * @param customerId the id of the customer to archive
     * @param rowVersion the version of the customer the caller based the change on
     * @return the archived customer
     */
    @Transactional
    public CustomerResponse archive(@NotNull UUID customerId, @NotNull Long rowVersion) {
        Customer customer = findActive(customerId);
        checkVersion(customer, rowVersion);

        // The customer's addresses go with the customer.
        serviceAddressService.archiveAllOfCustomer(customerId);

        customer.archive(Instant.now());
        customerRepository.flush();
        auditService.recordInTransaction(AuditAction.ARCHIVE, AUDIT_ENTITY_TYPE, customerId, Map.of());
        return toResponse(customer);
    }

    private Customer findActive(UUID customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.NOT_FOUND,
                        "Customer " + customerId + " not found."));
    }

    // Optimistic concurrency: the caller must have seen the current version.
    private void checkVersion(Customer customer, Long baseVersion) {
        if (!Objects.equals(customer.getRowVersion(), baseVersion)) {
            throw new ApplicationException(ErrorCode.VERSION_CONFLICT,
                    "The customer was modified by someone else.", false, customer.getRowVersion());
        }
    }

    private CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(customer.getCustomerId(), customer.getCustomerName(), customer.getEmail(),
                customer.getPhone(), customer.getNotes(), customer.getRowVersion());
    }

}
