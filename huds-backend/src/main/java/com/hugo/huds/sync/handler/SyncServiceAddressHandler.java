package com.hugo.huds.sync.handler;

import com.hugo.huds.address.dto.ServiceAddressResponse;
import com.hugo.huds.address.dto.ServiceAddressRequest;
import com.hugo.huds.address.service.ServiceAddressService;
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
 * @date 2026-10-20 1:33 p.m.
 */
@Component
public class SyncServiceAddressHandler extends SyncAbstractHandler {

    private final ServiceAddressService serviceAddressService;

    public SyncServiceAddressHandler(SyncMutationRepository syncMutationRepository, ObjectMapper objectMapper,
                                     PlatformTransactionManager txManager, Validator validator, ServiceAddressService serviceAddressService) {
        super(syncMutationRepository, objectMapper, txManager, validator);
        this.serviceAddressService = serviceAddressService;
    }


    @Override
    protected SyncPushResultResponse doHandle(SyncPushMutationRequest syncPushRequest) {

        SyncOperation operation = syncPushRequest.syncOperation();

        ServiceAddressResponse resultResponse = null;
        UUID serviceAddressId = syncPushRequest.entityId();
        switch (operation) {
            case CREATE ->
                    resultResponse = serviceAddressService.add(serviceAddressId, readPayload(syncPushRequest, ServiceAddressRequest.class));
            case UPDATE ->
                    resultResponse = serviceAddressService.update(serviceAddressId, syncPushRequest.rowVersion(),readPayload(syncPushRequest, ServiceAddressRequest.class));
            case ARCHIVE -> resultResponse = serviceAddressService.archive(serviceAddressId,syncPushRequest.rowVersion());
            default ->
                    throw new ApplicationException(ErrorCode.INVALID_PAYLOAD, "Unsupported operation for SERVICE_ADDRESS: " + syncPushRequest.syncOperation());
        }
        return SyncPushResultResponse.accepted(syncPushRequest, resultResponse.rowVersion());
    }


    @Override
    public SyncEntityType getSyncEntityType() {
        return SyncEntityType.SERVICE_ADDRESS;
    }
}
