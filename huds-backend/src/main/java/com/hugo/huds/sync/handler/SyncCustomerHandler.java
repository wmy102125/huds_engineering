package com.hugo.huds.sync.handler;

import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.customer.dto.CustomerRequest;
import com.hugo.huds.customer.dto.CustomerResponse;
import com.hugo.huds.customer.service.CustomerService;
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
public class SyncCustomerHandler extends SyncAbstractHandler {

    private final CustomerService customerService;

    public SyncCustomerHandler(SyncMutationRepository syncMutationRepository, ObjectMapper objectMapper,
                               PlatformTransactionManager txManager, Validator validator, CustomerService customerService) {
        super(syncMutationRepository, objectMapper, txManager, validator);
        this.customerService = customerService;
    }


    @Override
    protected SyncPushResultResponse doHandle(SyncPushMutationRequest syncPushRequest) {

        SyncOperation operation = syncPushRequest.syncOperation();

        CustomerResponse resultResponse = null;
        UUID customerId = syncPushRequest.entityId();
        switch (operation) {
            case CREATE ->
                    resultResponse = customerService.add(customerId, readPayload(syncPushRequest, CustomerRequest.class));
            case UPDATE ->
                    resultResponse = customerService.update(customerId, syncPushRequest.rowVersion(),readPayload(syncPushRequest, CustomerRequest.class));
            case ARCHIVE -> resultResponse = customerService.archive(customerId,syncPushRequest.rowVersion());
            default ->
                    throw new ApplicationException(ErrorCode.INVALID_PAYLOAD, "Unsupported operation for CUSTOMER: " + syncPushRequest.syncOperation());
        }
        return SyncPushResultResponse.accepted(syncPushRequest, resultResponse.rowVersion());
    }


    @Override
    public SyncEntityType getSyncEntityType() {
        return SyncEntityType.CUSTOMER;
    }
}
