package com.hugo.huds.sync.handler;

import com.hugo.huds.appliance.dto.ApplianceModelRequest;
import com.hugo.huds.appliance.dto.ApplianceModelResponse;
import com.hugo.huds.appliance.dto.ApplianceServiceAddressRequest;
import com.hugo.huds.appliance.dto.ApplianceServiceAddressResponse;
import com.hugo.huds.appliance.service.ApplianceModelService;
import com.hugo.huds.appliance.service.ApplianceServiceAddressService;
import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
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
public class SyncApplianceServiceAddressHandler implements SyncMutationHandler {

    private final ApplianceServiceAddressService applianceServiceAddressService;
    private final SyncPayloadReader payloadReader;

    public SyncApplianceServiceAddressHandler(ApplianceServiceAddressService applianceServiceAddressService,
                                              SyncPayloadReader payloadReader) {
        this.applianceServiceAddressService = applianceServiceAddressService;
        this.payloadReader = payloadReader;
    }

    @Override
    public SyncEntityType getSyncEntityType() {
        return SyncEntityType.APPLIANCE_SERVICE_ADDRESS;
    }

    @Override
    public Long handle(UUID actorUserId, SyncPushMutationRequest mutation) {
        UUID applianceServiceAddressId = mutation.entityId();
        return switch (mutation.syncOperation()) {
            case CREATE -> applianceServiceAddressService.add(applianceServiceAddressId,
                    payloadReader.read(mutation, ApplianceServiceAddressRequest.class)).rowVersion();
            case UPDATE -> applianceServiceAddressService.update(applianceServiceAddressId,
                    payloadReader.requireRowVersion(mutation),
                    payloadReader.read(mutation, ApplianceServiceAddressRequest.class)).rowVersion();
            case ARCHIVE -> applianceServiceAddressService.archive(applianceServiceAddressId,
                    payloadReader.requireRowVersion(mutation)).rowVersion();
            default -> throw new ApplicationException(ErrorCode.INVALID_PAYLOAD,
                    "Unsupported operation for APPLIANCE_SERVICE_ADDRESS: " + mutation.syncOperation());
        };
    }
}
