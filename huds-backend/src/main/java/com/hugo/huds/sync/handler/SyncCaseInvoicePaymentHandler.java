package com.hugo.huds.sync.handler;

import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.invoice.dto.CaseInvoicePaymentRequest;
import com.hugo.huds.invoice.service.CaseInvoicePaymentService;
import com.hugo.huds.sync.dispatcher.SyncEntityType;
import com.hugo.huds.sync.dto.SyncPullResponse;
import com.hugo.huds.sync.dto.SyncPushMutationRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Sync handler of a payment of a bill (entityType CASE_INVOICE_PAYMENT). The entityId of a mutation is the id of the row.
 * It is never archived, so only CREATE and UPDATE are supported.
 */
@Component
public class SyncCaseInvoicePaymentHandler implements SyncHandler {

    private final CaseInvoicePaymentService caseInvoicePaymentService;
    private final SyncPayloadReader payloadReader;

    public SyncCaseInvoicePaymentHandler(CaseInvoicePaymentService caseInvoicePaymentService, SyncPayloadReader payloadReader) {
        this.caseInvoicePaymentService = caseInvoicePaymentService;
        this.payloadReader = payloadReader;
    }

    @Override
    public SyncEntityType getSyncEntityType() {
        return SyncEntityType.CASE_INVOICE_PAYMENT;
    }

    @Override
    public Long pushHandle(UUID actorUserId, SyncPushMutationRequest mutation) {
        return switch (mutation.operation()) {
            case CREATE -> caseInvoicePaymentService.add(mutation.entityId(),
                    payloadReader.read(mutation, CaseInvoicePaymentRequest.class));
            case UPDATE -> caseInvoicePaymentService.update(mutation.entityId(),
                    payloadReader.requireRowVersion(mutation),
                    payloadReader.read(mutation, CaseInvoicePaymentRequest.class));
            default -> throw new ApplicationException(ErrorCode.INVALID_PAYLOAD,
                    "Unsupported operation for CASE_INVOICE_PAYMENT: " + mutation.operation());
        };
    }

    @Override
    public SyncPullResponse pullHandle(Instant since, UUID sinceId, int limit) {
        return caseInvoicePaymentService.fetchChanges(since, sinceId, limit);
    }
}
