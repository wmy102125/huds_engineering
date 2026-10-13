package com.hugo.huds.common.exception;

import com.hugo.huds.common.api.ApiErrorResponse;
import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.trace.CorrelationIdFilter;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessException(
            BusinessException ex,
            HttpServletRequest request
    ) {

        ApiErrorResponse response = new ApiErrorResponse(
                ex.getErrorCode().name(),
                ex.getMessage(),
                ex.isRetryable(),
                ex.getCurrentVersion(),
                getCorrelationId(request)
        );

        return ResponseEntity
                .status(ex.getErrorCode().getHttpStatus())
                .body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {

        ApiErrorResponse response = new ApiErrorResponse(
                ErrorCode.INVALID_PAYLOAD.name(),
                "Request validation failed.",
                false,
                null,
                getCorrelationId(request)
        );

        return ResponseEntity
                .badRequest()
                .body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(
            Exception ex,
            HttpServletRequest request
    ) {

        log.error("Unexpected server error", ex);

        ApiErrorResponse response = new ApiErrorResponse(
                ErrorCode.INTERNAL_ERROR.name(),
                "An unexpected server error occurred.",
                false,
                null,
                getCorrelationId(request)
        );

        return ResponseEntity
                .internalServerError()
                .body(response);
    }

    private String getCorrelationId(HttpServletRequest request) {

        Object value = request.getAttribute(
                CorrelationIdFilter.CORRELATION_ID
        );

        return value == null ? null : value.toString();
    }
}