package com.hugo.huds.common.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import com.hugo.huds.common.api.ApiErrorResponse;
import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.error.FieldViolation;
import com.hugo.huds.common.trace.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Comparator;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // A violation raised on a service method parameter means the caller passed invalid input.
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        // Log only where and why validation failed. The invalid value is left out because it may be personal data.
        ex.getConstraintViolations().forEach(violation ->
                log.warn("Constraint violation on {} {}: {} {}", request.getMethod(), request.getRequestURI(),
                        violation.getPropertyPath(), violation.getMessage()));

        ApiErrorResponse response = new ApiErrorResponse(
                ErrorCode.INVALID_PAYLOAD.name(),
                "Request validation failed.",
                false,
                null,
                getCorrelationId(request),
                null,
                null
        );
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ApiErrorResponse> handleApplicationException(ApplicationException ex, HttpServletRequest request) {
        ApiErrorResponse response = new ApiErrorResponse(
                ex.getErrorCode().name(),
                ex.getMessage(),
                ex.isRetryable(),
                ex.getCurrentVersion(),
                getCorrelationId(request), ex.getConflictingResourceId(), null
        );
        return ResponseEntity.status(ex.getErrorCode().getHttpStatus()).body(response);
    }

    // inside GlobalExceptionHandler
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        // Raised by unique indexes when two requests race past the service-level duplicate check.
        // Log the type only: the exception message contains the duplicated value (personal data).
        log.warn("Data integrity violation on {} {}", request.getMethod(), request.getRequestURI());
        return build(ErrorCode.DUPLICATE_RESOURCE, "The resource already exists.", request);
    }

    // Authentication failures raised inside a controller must never become a 500.
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
        return build(ErrorCode.UNAUTHENTICATED, "Authentication is required.", request);
    }

    // Raised by @PreAuthorize (method security), which runs inside the controller call.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(ErrorCode.FORBIDDEN, "Access is denied.", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(ErrorCode.INVALID_PAYLOAD, "Request body is missing or malformed.", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return build(ErrorCode.NOT_FOUND, "Resource not found.", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(Exception ex, HttpServletRequest request) {
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
                null,
                violations
        );

        return ResponseEntity.status(ErrorCode.INVALID_PAYLOAD.getHttpStatus()).body(response);
    }

    // A required query parameter is absent, or it was present but converted to null (for example an empty enum value).
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex, HttpServletRequest request) {
        // Only the parameter name is echoed back; the submitted value is never included.
        return build(ErrorCode.INVALID_PAYLOAD,
                "Required request parameter is missing or empty: " + ex.getParameterName(), request);
    }

    // A query or path parameter could not be converted to the expected type (bad UUID, unknown enum value, ...).
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        // The submitted value is not echoed back because it may contain personal data.
        return build(ErrorCode.INVALID_PAYLOAD,
                "Request parameter has an invalid value: " + ex.getName(), request);
    }

    private ResponseEntity<ApiErrorResponse> build(ErrorCode code, String message, HttpServletRequest request) {
        ApiErrorResponse response = new ApiErrorResponse(
                code.name(), message, false, null, getCorrelationId(request), null, null);
        return ResponseEntity.status(code.getHttpStatus()).body(response);
    }

    private String getCorrelationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.CORRELATION_ID);
        return value == null ? null : value.toString();
    }
}