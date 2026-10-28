package com.hugo.huds.sync.handler;

import com.hugo.huds.address.dto.ServiceAddressRequest;
import com.hugo.huds.address.service.ServiceAddressService;
import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.sync.dispatcher.SyncEntityType;
import com.hugo.huds.sync.dto.SyncPullResponse;
import com.hugo.huds.sync.dto.SyncPushMutationRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Applies SERVICE_ADDRESS mutations by calling {@link ServiceAddressService}.
 *
 * @author Meiyan Wang
 * @date 2026-10-20 1:33 p.m.
 */
@Component
public class SyncServiceAddressHandler implements SyncHandler {

    private final ServiceAddressService serviceAddressService;
    private final SyncPayloadReader payloadReader;

    public SyncServiceAddressHandler(ServiceAddressService serviceAddressService, SyncPayloadReader payloadReader) {
        this.serviceAddressService = serviceAddressService;
        this.payloadReader = payloadReader;
    }

    @Override
    public SyncEntityType getSyncEntityType() {
        return SyncEntityType.SERVICE_ADDRESS;
    }

    @Override
    public Long pushHandle(UUID actorUserId, SyncPushMutationRequest mutation) {
        UUID serviceAddressId = mutation.entityId();
        return switch (mutation.operation()) {
            case CREATE -> serviceAddressService.add(serviceAddressId,
                    payloadReader.read(mutation, ServiceAddressRequest.class)).rowVersion();
            case UPDATE -> serviceAddressService.update(serviceAddressId, payloadReader.requireRowVersion(mutation),
                    payloadReader.read(mutation, ServiceAddressRequest.class)).rowVersion();
            case ARCHIVE -> serviceAddressService.archive(serviceAddressId,
                    payloadReader.requireRowVersion(mutation)).rowVersion();
            default -> throw new ApplicationException(ErrorCode.INVALID_PAYLOAD,
                    "Unsupported operation for SERVICE_ADDRESS: " + mutation.operation());
        };
    }

    @Override
    public SyncPullResponse pullHandle(Instant since, UUID sinceId, int limit) {
        return serviceAddressService.fetchChanges(since, sinceId, limit);
    }
}