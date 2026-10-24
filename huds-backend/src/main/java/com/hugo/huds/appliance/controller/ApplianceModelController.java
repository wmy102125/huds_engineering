package com.hugo.huds.appliance.controller;

import com.hugo.huds.appliance.dto.ApplianceModelDetailResponse;
import com.hugo.huds.appliance.dto.ApplianceModelResponse;
import com.hugo.huds.appliance.dto.ApplianceModelType;
import com.hugo.huds.appliance.service.ApplianceModelService;
import com.hugo.huds.auth.support.RolePermissions;
import com.hugo.huds.common.response.PageResponse;
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
 * @date 2026-10-23 10:22 p.m.
 */
@RestController
@RequestMapping("/api/v1/appliance/model")
public class ApplianceModelController {

    private final ApplianceModelService applianceModelService;

    public ApplianceModelController(ApplianceModelService applianceModelService) {
        this.applianceModelService = applianceModelService;
    }

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('" + RolePermissions.CUSTOMER_WRITE + "')")
    public ResponseEntity<List<ApplianceModelResponse>> list(@Param("applianceModelType") ApplianceModelType applianceModelType,
                                                             @Param("brand") String brand,
                                                             @Param("modelNo") String modelNo) {
        return ResponseEntity.ok(applianceModelService.allList(applianceModelType, brand, modelNo));
    }

    @GetMapping("/pageList")
    @PreAuthorize("hasAuthority('" + RolePermissions.CUSTOMER_WRITE + "')")
    public ResponseEntity<PageResponse<ApplianceModelResponse>> pageList(@Param("applianceModelType") ApplianceModelType applianceModelType,
                                                                         @Param("brand") String brand,
                                                                         @Param("modelNo") String modelNo,
                                                                         @Param("page") int page,
                                                                         @Param("size") int size) {
        return ResponseEntity.ok(applianceModelService.pageList(applianceModelType, brand, modelNo, page, size));
    }


    @GetMapping("/{applianceModelId}")
    @PreAuthorize("hasAuthority('" + RolePermissions.CUSTOMER_WRITE + "')")
    public ResponseEntity<ApplianceModelDetailResponse> detail(@PathVariable UUID applianceModelId) {
        return ResponseEntity.ok(applianceModelService.detail(applianceModelId));
    }
}
