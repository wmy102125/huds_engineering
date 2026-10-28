package com.hugo.huds.common.exception;

import com.hugo.huds.common.error.ErrorCode;

import java.util.UUID;


public class ApplicationException extends RuntimeException {

    private final ErrorCode errorCode;
    private final boolean retryable;
    private final Long currentVersion;
    /** The id of the existing record that caused a DUPLICATE_RESOURCE error, otherwise null. */
    private UUID conflictingResourceId;

    public ApplicationException(ErrorCode errorCode,String message ) {
        this(errorCode, message, false, null);
    }

    public ApplicationException(ErrorCode errorCode,String message,boolean retryable, Long currentVersion) {
        super(message);
        this.errorCode = errorCode;
        this.retryable = retryable;
        this.currentVersion = currentVersion;
    }
    /**
     * Creates a duplicate-resource error that tells the client which existing record to use instead.
     *
     * @param message               the message for the client, without personal data
     * @param conflictingResourceId the id of the existing record
     * @return the exception to throw
     */
    public static ApplicationException duplicate(String message, UUID conflictingResourceId) {
        ApplicationException exception = new ApplicationException(ErrorCode.DUPLICATE_RESOURCE, message);
        exception.conflictingResourceId = conflictingResourceId;
        return exception;
    }

    public UUID getConflictingResourceId() {
        return conflictingResourceId;
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