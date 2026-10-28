package com.hugo.huds.appliance.controller;

import com.hugo.huds.appliance.dto.ApplianceDetailResponse;
import com.hugo.huds.appliance.dto.ApplianceResponse;
import com.hugo.huds.appliance.service.ApplianceService;
import com.hugo.huds.auth.support.RolePermissions;
import com.hugo.huds.common.response.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * @author Meiyan Wang
 * @date 2026-10-26 1:08 a.m.
 */
@RestController
@RequestMapping("/api/v1/appliances")
public class ApplianceController {

    private final ApplianceService applianceService;

    public ApplianceController(ApplianceService applianceService) {
        this.applianceService = applianceService;
    }

    @GetMapping("/list")
    public ResponseEntity<List<ApplianceResponse>> list(@RequestParam(value = "applianceModelId") UUID applianceModelId,
                                                        @RequestParam(value = "serialNo", required = false) String serialNo) {
        return ResponseEntity.ok(applianceService.allList(applianceModelId, serialNo));
    }

    @GetMapping("/pageList")
    public ResponseEntity<PageResponse<ApplianceResponse>> pageList(@RequestParam(value = "applianceModelId", required = false) UUID applianceModelId,
                                                                    @RequestParam(value = "serialNo", required = false) String serialNo,
                                                                    @RequestParam(defaultValue = "0") int page,
                                                                    @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(applianceService.pageList(applianceModelId, serialNo, page, size));
    }


    @GetMapping("/{applianceId}")
    public ResponseEntity<ApplianceDetailResponse> detail(@PathVariable UUID applianceId) {
        return ResponseEntity.ok(applianceService.detail(applianceId));
    }
}
