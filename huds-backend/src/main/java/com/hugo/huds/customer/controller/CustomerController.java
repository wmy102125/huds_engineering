package com.hugo.huds.customer.controller;

import com.hugo.huds.auth.support.RolePermissions;
import com.hugo.huds.common.response.ExistsResponse;
import com.hugo.huds.common.response.PageResponse;
import com.hugo.huds.customer.dto.CustomerDetailResponse;
import com.hugo.huds.customer.dto.CustomerResponse;
import com.hugo.huds.customer.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

//    @PostMapping
//    @PreAuthorize("hasAuthority('" + RolePermissions.CUSTOMER_WRITE + "')")
//    public ResponseEntity<CreateCustomerResponse> addCustomer(@Valid @RequestBody CustomerRequest createCustomerRequest) {
//        // Call the service to add a new customer
//        CreateCustomerResponse customerResponse = customerService.addCustomer(createCustomerRequest);
//
//        // Location: /api/v1/customers/{customerId}
//        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
//                .path("/{customerId}")
//                .buildAndExpand(customerResponse.customerId())
//                .toUri();
//        return ResponseEntity.created(location).body(customerResponse);
//    }
//
//    @PutMapping("/{customerId}")
//    @PreAuthorize("hasAuthority('" + RolePermissions.CUSTOMER_WRITE + "')")
//    public ResponseEntity<CreateCustomerResponse> updateCustomer(@PathVariable UUID customerId, @Valid @RequestBody CustomerRequest updateCustomerRequest) {
//        // Call the service to add a new customer
//        return ResponseEntity.ok(customerService.updateCustomer(customerId,updateCustomerRequest));
//    }


    @GetMapping ("/{customerId}")
    @PreAuthorize("hasAuthority('" + RolePermissions.CUSTOMER_WRITE + "')")
    public ResponseEntity<CustomerDetailResponse> detail(@PathVariable UUID customerId) {
        return ResponseEntity.ok(customerService.detail(customerId));
    }

    @GetMapping("/existsEmail")
    //@PreAuthorize("hasAuthority('" + RolePermissions.CUSTOMER_WRITE + "')")
    public ResponseEntity<ExistsResponse> existsEmail(@RequestParam("email") String email) {
        return ResponseEntity.ok(customerService.existsEmail(email));
    }

    @GetMapping("/list")
    public ResponseEntity<List<CustomerResponse>> list(@RequestParam(value = "customerName",required = false) String customerName,
                                                       @RequestParam(value = "email",required = false) String email,
                                                       @RequestParam(value = "phone",required = false) String phone) {
        return ResponseEntity.ok(customerService.allList(customerName, email, phone));
    }

    @GetMapping("/pageList")
    public ResponseEntity<PageResponse<CustomerResponse>> pageList(@RequestParam(value = "customerName",required = false) String customerName,
                                                                   @RequestParam(value = "email",required = false) String email,
                                                                   @RequestParam(value = "phone",required = false) String phone,
                                                                   @RequestParam(defaultValue = "0") int page,
                                                                   @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(customerService.pageList(customerName, email, phone, page, size));
    }
}
