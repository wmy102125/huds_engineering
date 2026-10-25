package com.hugo.huds.sync.handler;

import com.hugo.huds.appliance.dto.ApplianceModelRequest;
import com.hugo.huds.appliance.dto.ApplianceModelResponse;
import com.hugo.huds.appliance.service.ApplianceModelService;
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
public class SyncApplianceModelHandler implements SyncMutationHandler {

    private final ApplianceModelService applianceModelService;
    private final SyncPayloadReader payloadReader;

    public SyncApplianceModelHandler(ApplianceModelService applianceModelService, SyncPayloadReader payloadReader) {
        this.applianceModelService = applianceModelService;
        this.payloadReader = payloadReader;
    }

    @Override
    public SyncEntityType getSyncEntityType() {
        return SyncEntityType.APPLIANCE_MODEL;
    }

    @Override
    public Long handle(UUID actorUserId, SyncPushMutationRequest mutation) {
        UUID applianceModelId = mutation.entityId();
        return switch (mutation.syncOperation()) {
            case CREATE -> applianceModelService.add(applianceModelId,
                    payloadReader.read(mutation, ApplianceModelRequest.class)).rowVersion();
            case UPDATE -> applianceModelService.update(applianceModelId, payloadReader.requireRowVersion(mutation),
                    payloadReader.read(mutation, ApplianceModelRequest.class)).rowVersion();
            case ARCHIVE -> applianceModelService.archive(applianceModelId,
                    payloadReader.requireRowVersion(mutation)).rowVersion();
            default -> throw new ApplicationException(ErrorCode.INVALID_PAYLOAD,
                    "Unsupported operation for APPLIANCE_MODEL: " + mutation.syncOperation());
        };
    }
}
