package com.hugo.huds.sync.handler;

import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.sync.dto.SyncPushMutationRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;
/**
 * Reads and validates the payload of a mutation. Shared by all handlers.
 * @author Meiyan Wang
 * @date 2026-10-24 9:20 p.m.
 */
@Component
public class SyncPayloadReader {
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public SyncPayloadReader(ObjectMapper objectMapper, Validator validator) {
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    /**
     * Reads the mutation payload into a request object and validates it.
     *
     * @param request the mutation
     * @param type    the request class the payload is read into
     * @return the validated payload
     * @throws ApplicationException with {@code INVALID_PAYLOAD} if the payload is missing or invalid
     */
    public <T> T read(SyncPushMutationRequest request, Class<T> type) {
        JsonNode payload = request.payload();
        if (payload == null || payload.isNull()) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD, "Payload is required.");
        }
        T value;
        try {
            value = objectMapper.treeToValue(payload, type);
        } catch (RuntimeException ex) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD, "Payload has an invalid format.");
        }
        Set<ConstraintViolation<T>> violations = validator.validate(value);
        if (!violations.isEmpty()) {
            ConstraintViolation<T> first = violations.iterator().next();
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD,
                    first.getPropertyPath() + " " + first.getMessage());
        }
        return value;
    }

    /**
     * Returns the row version the client based the change on.
     *
     * @param request an UPDATE or ARCHIVE mutation
     * @return the row version
     * @throws ApplicationException with {@code INVALID_PAYLOAD} if the version is missing
     */
    public Long requireRowVersion(SyncPushMutationRequest request) {
        if (request.rowVersion() == null) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD,
                    "rowVersion is required for " + request.operation() + ".");
        }
        return request.rowVersion();
    }
}
