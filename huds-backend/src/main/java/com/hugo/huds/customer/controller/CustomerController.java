package com.hugo.huds.customer.controller;

import com.hugo.huds.auth.support.RolePermissions;
import com.hugo.huds.common.response.ExistsResponse;
import com.hugo.huds.customer.dto.CustomerRequest;
import com.hugo.huds.customer.dto.CreateCustomerResponse;
import com.hugo.huds.customer.dto.CustomerResponse;
import com.hugo.huds.customer.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.data.repository.query.Param;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + RolePermissions.CUSTOMER_WRITE + "')")
    public ResponseEntity<CreateCustomerResponse> addCustomer(@Valid @RequestBody CustomerRequest createCustomerRequest) {
        // Call the service to add a new customer
        CreateCustomerResponse customerResponse = customerService.addCustomer(createCustomerRequest);

        // Location: /api/v1/customers/{customerId}
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{customerId}")
                .buildAndExpand(customerResponse.customerId())
                .toUri();
        return ResponseEntity.created(location).body(customerResponse);
    }

    @PutMapping("/{customerId}")
    @PreAuthorize("hasAuthority('" + RolePermissions.CUSTOMER_WRITE + "')")
    public ResponseEntity<CreateCustomerResponse> updateCustomer(@PathVariable UUID customerId, @Valid @RequestBody CustomerRequest updateCustomerRequest) {
        // Call the service to add a new customer
        return ResponseEntity.ok(customerService.updateCustomer(customerId,updateCustomerRequest));
    }

    @GetMapping("/{customerId}")
    @PreAuthorize("hasAuthority('" + RolePermissions.CUSTOMER_WRITE + "')")
    public ResponseEntity<CustomerResponse> detailCustomer(@PathVariable UUID customerId) {
        return ResponseEntity.ok(customerService.detailCustomer(customerId));
    }

    @GetMapping("/existsEmail")
    @PreAuthorize("hasAuthority('" + RolePermissions.CUSTOMER_WRITE + "')")
    public ResponseEntity<ExistsResponse> existsEmail(@Valid @Param("email") String email) {
        return ResponseEntity.ok(customerService.existsEmail(email));
    }

}
