package com.hugo.huds.sync.handler;

import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.invoice.dto.CaseInvoiceRequest;
import com.hugo.huds.invoice.service.CaseInvoiceService;
import com.hugo.huds.sync.dispatcher.SyncEntityType;
import com.hugo.huds.sync.dto.SyncPullResponse;
import com.hugo.huds.sync.dto.SyncPushMutationRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Sync handler of the bill of a case (entityType CASE_INVOICE). The entityId of a mutation is the id of the row.
 * It is never archived, so only CREATE and UPDATE are supported.
 */
@Component
public class SyncCaseInvoiceHandler implements SyncHandler {

    private final CaseInvoiceService caseInvoiceService;
    private final SyncPayloadReader payloadReader;

    public SyncCaseInvoiceHandler(CaseInvoiceService caseInvoiceService, SyncPayloadReader payloadReader) {
        this.caseInvoiceService = caseInvoiceService;
        this.payloadReader = payloadReader;
    }

    @Override
    public SyncEntityType getSyncEntityType() {
        return SyncEntityType.CASE_INVOICE;
    }

    @Override
    public Long pushHandle(UUID actorUserId, SyncPushMutationRequest mutation) {
        return switch (mutation.operation()) {
            case CREATE -> caseInvoiceService.add(mutation.entityId(),
                    payloadReader.read(mutation, CaseInvoiceRequest.class));
            case UPDATE -> caseInvoiceService.update(mutation.entityId(),
                    payloadReader.requireRowVersion(mutation),
                    payloadReader.read(mutation, CaseInvoiceRequest.class));
            default -> throw new ApplicationException(ErrorCode.INVALID_PAYLOAD,
                    "Unsupported operation for CASE_INVOICE: " + mutation.operation());
        };
    }

    @Override
    public SyncPullResponse pullHandle(Instant since, UUID sinceId, int limit) {
        return caseInvoiceService.fetchChanges(since, sinceId, limit);
    }
}
