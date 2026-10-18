package com.hugo.huds.customer.service;

import com.hugo.huds.address.entity.ServiceAddress;
import com.hugo.huds.address.repository.ServiceAddressRepository;
import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.BusinessException;
import com.hugo.huds.customer.dto.CreateCustomerRequest;
import com.hugo.huds.customer.dto.CustomerResponse;
import com.hugo.huds.customer.entity.Customer;
import com.hugo.huds.customer.repository.CustomerRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public CustomerResponse addCustomer(CreateCustomerRequest createCustomerRequest) {
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
        return new CustomerResponse(customer.getCustomerId());
    }


}
