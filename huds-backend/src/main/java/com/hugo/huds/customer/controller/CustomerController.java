package com.hugo.huds.customer.controller;

import com.hugo.huds.auth.support.RolePermissions;
import com.hugo.huds.customer.dto.CreateCustomerRequest;
import com.hugo.huds.customer.dto.CustomerResponse;
import com.hugo.huds.customer.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + RolePermissions.CUSTOMER_WRITE + "')")
    public ResponseEntity<CustomerResponse> addCustomer(@Valid @RequestBody CreateCustomerRequest createCustomerRequest) {
        // Call the service to add a new customer
        CustomerResponse customerResponse = customerService.addCustomer(createCustomerRequest);

        // Location: /api/v1/customers/{customerId}
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{customerId}")
                .buildAndExpand(customerResponse.customerId())
                .toUri();
        return ResponseEntity.created(location).body(customerResponse);
    }

}
