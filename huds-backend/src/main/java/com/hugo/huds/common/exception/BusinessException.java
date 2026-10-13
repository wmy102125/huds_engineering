package com.hugo.huds.common.exception;

import com.hugo.huds.common.error.ErrorCode;

public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final boolean retryable;
    private final Long currentVersion;

    public BusinessException(
            ErrorCode errorCode,
            String message
    ) {
        this(errorCode, message, false, null);
    }

    public BusinessException(
            ErrorCode errorCode,
            String message,
            boolean retryable,
            Long currentVersion
    ) {
        super(message);

        this.errorCode = errorCode;
        this.retryable = retryable;
        this.currentVersion = currentVersion;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public Long getCurrentVersion() {
        return currentVersion;
    }
}