package com.hugo.huds.customer.service;

import com.hugo.huds.address.entity.ServiceAddress;
import com.hugo.huds.address.repository.ServiceAddressRepository;
import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.BusinessException;
import com.hugo.huds.common.response.ExistsResponse;
import com.hugo.huds.customer.dto.CustomerRequest;
import com.hugo.huds.customer.dto.CreateCustomerResponse;
import com.hugo.huds.customer.dto.CustomerResponse;
import com.hugo.huds.customer.entity.Customer;
import com.hugo.huds.customer.repository.CustomerRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.UUID;

/**
 * @author meiyan wang
 * @date 2026-10-16 6:14 p.m.
 */
@Service
@Slf4j
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final ServiceAddressRepository serviceAddressRepository;

    public CustomerService(CustomerRepository customerRepository, ServiceAddressRepository serviceAddressRepository) {
        this.customerRepository = customerRepository;
        this.serviceAddressRepository = serviceAddressRepository;
    }

    @Transactional
    public CreateCustomerResponse addCustomer(CustomerRequest createCustomerRequest) {
        //add a new customer to the database, if the email already exists, throw an exception
        if (createCustomerRequest.email() != null && !createCustomerRequest.email().isBlank()) {
            // Email is optional, skip the duplicate check if email is not provided
            Customer customer = customerRepository.findByEmailIgnoreCase(createCustomerRequest.email())
                    .stream()
                    .findFirst()
                    .orElse(null);
            if (customer != null) {
                throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "Customer with email " + createCustomerRequest.email() + " already exists");
            }
        }

        Customer customer = new Customer();
        BeanUtils.copyProperties(createCustomerRequest, customer);
        customerRepository.save(customer);

        // add a new service address for the customer
        ServiceAddress serviceAddress = new ServiceAddress();
        BeanUtils.copyProperties(createCustomerRequest, serviceAddress);
        serviceAddress.setCustomerId(customer.getCustomerId());
        serviceAddress.setIsDefault(true);
        serviceAddressRepository.save(serviceAddress);
        return new CreateCustomerResponse(customer.getCustomerId());
    }


    @Transactional
    public CreateCustomerResponse updateCustomer(UUID customerId, CustomerRequest updateCustomerRequest) {

        // find if the customerId was exists ,or throw error exception
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Customer " + customerId + " not found"));

        // find if the update
        if (updateCustomerRequest.email() != null && !updateCustomerRequest.email().isBlank()) {
            // Email is optional, skip the duplicate check if email is not provided
            boolean existsEmailCustomer = customerRepository.existsByEmailIgnoreCaseAndCustomerIdNot(updateCustomerRequest.email(), customerId);
            if (existsEmailCustomer) {
                throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "Another customer has already use this email " + updateCustomerRequest.email());
            }
        }
        customer.setCustomerName(updateCustomerRequest.customerName());
        customer.setEmail(updateCustomerRequest.email());
        customer.setPhone(updateCustomerRequest.phone());
        customer.setNotes(updateCustomerRequest.notes());

        //check if the address was exists, or throw error exception
        ServiceAddress serviceAddress = serviceAddressRepository.findServiceAddressByCustomerIdAndIsDefault(customerId, true)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Default address of customer " + customerId + "not found"));

        serviceAddress.setAddressLine1(updateCustomerRequest.addressLine1());
        serviceAddress.setAddressLine2(updateCustomerRequest.addressLine2());
        serviceAddress.setCity(updateCustomerRequest.city());
        serviceAddress.setProvince(updateCustomerRequest.province());
        serviceAddress.setPostalCode(updateCustomerRequest.postalCode());
        serviceAddress.setCountryCode(updateCustomerRequest.countryCode());
        return new CreateCustomerResponse(customerId);

    }

    public ExistsResponse existsEmail(String email) {
        return new ExistsResponse(customerRepository.existsByEmailIgnoreCase(email));
    }

    public CustomerResponse detailCustomer(UUID customerId) {

        //find the customer
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Customer not found"));
        //find the customer's default address
        ServiceAddress serviceAddress = serviceAddressRepository.findServiceAddressByCustomerIdAndIsDefault(customerId, true)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Default address of customer " + customerId + " not found"));

        return new CustomerResponse(customer.getCustomerId(), customer.getEmail(), customer.getCustomerName(), customer.getPhone(), customer.getNotes(),
                serviceAddress.getAddressLine1(), serviceAddress.getAddressLine2(), serviceAddress.getCity(), serviceAddress.getProvince(), serviceAddress.getPostalCode(), serviceAddress.getCountryCode());

    }


}
