package com.hugo.huds.customer.service;

import com.hugo.huds.address.entity.ServiceAddress;
import com.hugo.huds.address.repository.ServiceAddressRepository;
import com.hugo.huds.customer.dto.CustomerRequest;
import com.hugo.huds.customer.entity.Customer;
import com.hugo.huds.customer.repository.CustomerRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import tools.jackson.databind.util.BeanUtil;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.apache.logging.log4j.ThreadContext.isEmpty;

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
    public void addCustomer(CustomerRequest customerRequest) {
        //add a new customer to the database, if the email already exists, throw an exception
        Customer customer = customerRepository.findByEmailIgnoreCase(customerRequest.email())
                .stream()
                .findFirst()
                .orElse(null);
        if (customer != null) {
            throw new IllegalArgumentException("Customer with email " + customerRequest.email() + " already exists");
        }
        customer = new Customer();
        BeanUtils.copyProperties(customerRequest, Customer.class);
        customerRepository.save(customer);

        // add a new service address for the customer
        List<ServiceAddress> addresses = serviceAddressRepository.findByCustomerId(customer.getCustomerId());
        ServiceAddress serviceAddress = null;
        if(!CollectionUtils.isEmpty(addresses)){
            //if the address already exists, do not add a new one
            serviceAddress = addresses.stream()
                    .filter(address -> sameAddress(address, customerRequest))
                    .findFirst().orElse(null);
        }
        if (addresses.isEmpty()||serviceAddress == null) {
            serviceAddress = new ServiceAddress();
            BeanUtils.copyProperties(customerRequest, serviceAddress);
            serviceAddressRepository.save(serviceAddress);
        }

    }


    private static boolean sameAddress(ServiceAddress address, CustomerRequest request) {
        return normalize(address.getLine1()).equals(normalize(request.line1()))
                && normalize(address.getLine2()).equals(normalize(request.line2()))
                && normalize(address.getCity()).equals(normalize(request.city()))
                && normalizePostalCode(address.getPostalCode()).equals(normalizePostalCode(request.postalCode()));
    }

    // Trim, collapse repeated spaces, ignore case; null becomes an empty string.
    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    // "b1p1a1" and "B1P 1A1" are the same postal code.
    private static String normalizePostalCode(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }
}
