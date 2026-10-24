package com.hugo.huds.address.controller;

import com.hugo.huds.address.dto.ServiceAddressDetailResponse;
import com.hugo.huds.address.dto.ServiceAddressResponse;
import com.hugo.huds.address.service.ServiceAddressService;
import com.hugo.huds.auth.support.RolePermissions;
import org.springframework.data.repository.query.Param;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * @author Meiyan Wang
 * @date 2026-10-19 10:09 p.m.
 */
@RequestMapping("/api/v1/address")
@RestController
public class ServiceAddressController {
    private final ServiceAddressService serviceAddressService;

    public ServiceAddressController(ServiceAddressService serviceAddressService) {
        this.serviceAddressService = serviceAddressService;
    }

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('" + RolePermissions.CUSTOMER_WRITE + "')")
    public ResponseEntity<List<ServiceAddressResponse>> list(@Param("customerId") UUID customerId) {
        return ResponseEntity.ok(serviceAddressService.list(customerId));
    }

    @GetMapping("/{serviceAddressId}")
    @PreAuthorize("hasAuthority('" + RolePermissions.CUSTOMER_WRITE + "')")
    public ResponseEntity<ServiceAddressDetailResponse> detail(@PathVariable UUID serviceAddressId) {
        return ResponseEntity.ok(serviceAddressService.detail(serviceAddressId));
    }
}
