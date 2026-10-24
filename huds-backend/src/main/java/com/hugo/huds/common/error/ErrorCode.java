package com.hugo.huds.common.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    INVALID_PAYLOAD(HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),

    VERSION_CONFLICT(HttpStatus.CONFLICT),
    INVALID_TRANSITION(HttpStatus.CONFLICT),

    SAFETY_GATE_REQUIRED(HttpStatus.PRECONDITION_FAILED),
    AUTHORIZATION_REQUIRED(HttpStatus.PRECONDITION_FAILED),

    DOMAIN_RULE_VIOLATION(HttpStatus.UNPROCESSABLE_ENTITY),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    RETRY_LATER(HttpStatus.SERVICE_UNAVAILABLE),

    DUPLICATE_RESOURCE(HttpStatus.CONFLICT),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),

    // For the sync api error
    MUTATION_ID_REUSED(HttpStatus.CONFLICT),
    OUTBOX_OWNER_MISMATCH(HttpStatus.FORBIDDEN),
    CURSOR_EXPIRED(HttpStatus.GONE),;

    private final HttpStatus httpStatus;

    ErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

}