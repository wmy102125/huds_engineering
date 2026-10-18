package com.hugo.huds.common.exception;

import com.hugo.huds.common.api.ApiErrorResponse;
import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.error.FieldViolation;
import com.hugo.huds.common.trace.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Comparator;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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
                getCorrelationId(request), null
        );
        return ResponseEntity.status(ex.getErrorCode().getHttpStatus()).body(response);
    }

    // Authentication failures raised inside a controller must never become a 500.
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthenticationException(
            AuthenticationException ex,
            HttpServletRequest request
    ) {
        return build(ErrorCode.UNAUTHENTICATED, "Authentication is required.", request);
    }

    // Raised by @PreAuthorize (method security), which runs inside the controller call.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request
    ) {
        return build(ErrorCode.FORBIDDEN, "Access is denied.", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        return build(ErrorCode.INVALID_PAYLOAD, "Request body is missing or malformed.", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResource(
            NoResourceFoundException ex,
            HttpServletRequest request
    ) {
        return build(ErrorCode.NOT_FOUND, "Resource not found.", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(
            Exception ex,
            HttpServletRequest request
    ) {
        log.error("Unexpected server error", ex);
        return build(ErrorCode.INTERNAL_ERROR, "An unexpected server error occurred.", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldViolation(fe.getField(), fe.getDefaultMessage()))
                .sorted(Comparator.comparing(FieldViolation::field))
                .toList();
        // Name the offending fields in the summary message so it is useful even without parsing fieldErrors
        String fields = String.join(", ", violations.stream().map(FieldViolation::field).distinct().toList());
        ApiErrorResponse response = new ApiErrorResponse(
                ErrorCode.INVALID_PAYLOAD.name(),
                "Request validation failed: " + fields,
                false,
                null,
                getCorrelationId(request),
                violations
        );

        return ResponseEntity.status(ErrorCode.INVALID_PAYLOAD.getHttpStatus()).body(response);
    }

    private ResponseEntity<ApiErrorResponse> build(ErrorCode code, String message, HttpServletRequest request) {
        ApiErrorResponse response = new ApiErrorResponse(
                code.name(), message, false, null, getCorrelationId(request), null);
        return ResponseEntity.status(code.getHttpStatus()).body(response);
    }

    private String getCorrelationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.CORRELATION_ID);
        return value == null ? null : value.toString();
    }
}