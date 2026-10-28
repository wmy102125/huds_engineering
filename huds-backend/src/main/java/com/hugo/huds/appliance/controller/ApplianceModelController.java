package com.hugo.huds.appliance.controller;

import com.hugo.huds.appliance.dto.ApplianceModelDetailResponse;
import com.hugo.huds.appliance.dto.ApplianceModelResponse;
import com.hugo.huds.appliance.dto.ApplianceModelType;
import com.hugo.huds.appliance.service.ApplianceModelService;
import com.hugo.huds.auth.support.RolePermissions;
import com.hugo.huds.common.response.PageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * @author Meiyan Wang
 * @date 2026-10-23 10:22 p.m.
 */
@RestController
@RequestMapping("/api/v1/appliance-models")
public class ApplianceModelController {

    private final ApplianceModelService applianceModelService;

    public ApplianceModelController(ApplianceModelService applianceModelService) {
        this.applianceModelService = applianceModelService;
    }

    @GetMapping("/list")
    public ResponseEntity<List<ApplianceModelResponse>> list(@RequestParam(value = "applianceModelType", required = false) ApplianceModelType applianceModelType,
                                                             @RequestParam(value = "brand", required = false) String brand,
                                                             @RequestParam(value = "modelNo", required = false) String modelNo) {
        return ResponseEntity.ok(applianceModelService.allList(applianceModelType, brand, modelNo));
    }

    @GetMapping("/pageList")
    public ResponseEntity<PageResponse<ApplianceModelResponse>> pageList(@RequestParam(value = "applianceModelType", required = false) ApplianceModelType applianceModelType,
                                                                         @RequestParam(value = "brand", required = false) String brand,
                                                                         @RequestParam(value = "modelNo", required = false) String modelNo,
                                                                         @RequestParam(defaultValue = "0") int page,
                                                                         @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(applianceModelService.pageList(applianceModelType, brand, modelNo, page, size));
    }


    @GetMapping("/{applianceModelId}")
    public ResponseEntity<ApplianceModelDetailResponse> detail(@PathVariable UUID applianceModelId) {
        return ResponseEntity.ok(applianceModelService.detail(applianceModelId));
    }
}
