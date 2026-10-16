package com.hugo.huds.config;

import com.hugo.huds.common.api.ApiErrorResponse;
import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.trace.CorrelationIdFilter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;



/**
 * @author meiyan wang
 * @date 2026-10-16 9:43 a.m.
 * @description Writes 401/403 responses produced inside the spring security filter chain using the project's unified error body.
 */
@Component
public class RestAuthenticationHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private ObjectMapper objectMapper;

    public RestAuthenticationHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    //Not login in -> 401 Unauthorized
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        write(request, response, ErrorCode.UNAUTHENTICATED, "Authentication is required.", false);
    }

    //Login in but no permission -> 403 Forbidden. A CSRF failure is retryable: fetch a new token and resend.
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex) throws IOException, ServletException {
        if(ex instanceof CsrfException) {
            write(request, response, ErrorCode.FORBIDDEN, "CSRF token is invalid or missing.", true);
        } else {
            write(request, response, ErrorCode.FORBIDDEN, "Access is denied.", false);

        }
    }

    private void write(HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode, String message, boolean retryable) throws IOException {
        Object correlationId = request.getAttribute(CorrelationIdFilter.CORRELATION_ID);

        ApiErrorResponse body = new ApiErrorResponse(
                errorCode.name(), message, retryable, null,
                correlationId == null ? null : correlationId.toString());

        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
