package com.hugo.huds.sync.handler;

import com.hugo.huds.appliance.dto.ApplianceModelRequest;
import com.hugo.huds.appliance.dto.ApplianceModelResponse;
import com.hugo.huds.appliance.dto.ApplianceRequest;
import com.hugo.huds.appliance.dto.ApplianceResponse;
import com.hugo.huds.appliance.service.ApplianceService;
import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.customer.dto.CustomerRequest;
import com.hugo.huds.customer.dto.CustomerResponse;
import com.hugo.huds.sync.dispatcher.SyncEntityType;
import com.hugo.huds.sync.dispatcher.SyncOperation;
import com.hugo.huds.sync.dto.SyncPushMutationRequest;
import com.hugo.huds.sync.dto.SyncPushResultResponse;
import com.hugo.huds.sync.repository.SyncMutationRepository;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * @author Meiyan Wang
 * @date 2026-10-24 8:43 p.m.
 */
@Component
public class SyncApplianceHandler implements SyncMutationHandler {

    private final ApplianceService applianceService;
    private final SyncPayloadReader payloadReader;

    public SyncApplianceHandler(ApplianceService applianceService, SyncPayloadReader payloadReader) {
        this.applianceService = applianceService;
        this.payloadReader = payloadReader;
    }

    @Override
    public Long handle(UUID actorUserId, SyncPushMutationRequest request) {
        SyncOperation operation = request.syncOperation();

        ApplianceResponse resultResponse = null;
        UUID applianceId = request.entityId();
        switch (operation) {
            case CREATE ->
                    resultResponse = applianceService.add(applianceId, payloadReader.read(request, ApplianceRequest.class));
            case UPDATE ->
                    resultResponse = applianceService.update(applianceId, request.rowVersion(), payloadReader.read(request, ApplianceRequest.class));
            case ARCHIVE -> resultResponse = applianceService.archive(applianceId, request.rowVersion());
            default ->
                    throw new ApplicationException(ErrorCode.INVALID_PAYLOAD, "Unsupported operation for CUSTOMER: " + request.syncOperation());
        }
        return SyncPushResultResponse.accepted(request, resultResponse.rowVersion());
    }

    @Override
    public SyncEntityType getSyncEntityType() {
        return SyncEntityType.APPLIANCE;
    }
}
