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
public class SyncCustomerHandler implements SyncMutationHandler  {

    private final CustomerService customerService;
    private final SyncPayloadReader payloadReader;

    public SyncCustomerHandler(CustomerService customerService, SyncPayloadReader payloadReader) {
        this.customerService = customerService;
        this.payloadReader = payloadReader;
    }

    @Override
    public SyncEntityType getSyncEntityType() {
        return SyncEntityType.CUSTOMER;
    }

    @Override
    public Long handle(UUID actorUserId, SyncPushMutationRequest mutation) {
        UUID customerId = mutation.entityId();
        return switch (mutation.syncOperation()) {
            case CREATE -> customerService.add(customerId,
                    payloadReader.read(mutation, CustomerRequest.class)).rowVersion();
            case UPDATE -> customerService.update(customerId, payloadReader.requireRowVersion(mutation),
                    payloadReader.read(mutation, CustomerRequest.class)).rowVersion();
            case ARCHIVE -> customerService.archive(customerId,
                    payloadReader.requireRowVersion(mutation)).rowVersion();
            default -> throw new ApplicationException(ErrorCode.INVALID_PAYLOAD,
                    "Unsupported operation for CUSTOMER: " + mutation.syncOperation());
        };
    }
}
