package com.hugo.huds.auth.controller;

import com.hugo.huds.auth.dto.ResetPasswordRequest;
import com.hugo.huds.auth.dto.SessionInfoResponse;
import com.hugo.huds.auth.service.SessionAdminService;
import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.common.trace.CorrelationIdFilter;
import com.hugo.huds.user.repository.UsersRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * @author Meiyan Wang
 * @date 2026-10-16 1:37 p.m.
 * @description
 * Administrator operations: list or revoke a user's sessions ("lost device") and reset a password.
 * Guarded by the USER_MANAGE permission, not by a role name.
 */
@RestController
@RequestMapping("/api/v1/admin/users/{userId}")
@PreAuthorize("hasAuthority('USER_MANAGE')")
public class AdminUserController {

    private final SessionAdminService sessionAdminService;
    private final UsersRepository usersRepository;

    public AdminUserController(SessionAdminService sessionAdminService, UsersRepository usersRepository) {
        this.sessionAdminService = sessionAdminService;
        this.usersRepository = usersRepository;
    }
    @GetMapping("/sessions")
    public ResponseEntity<List<SessionInfoResponse>> sessions(@PathVariable UUID userId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(sessionAdminService.listSessions(userId));
    }

    @DeleteMapping("/sessions/{sessionRef}")
    public ResponseEntity<Void> revoke(@PathVariable UUID userId,
                                       @PathVariable String sessionRef,
                                       Authentication authentication,
                                       HttpServletRequest request) {
        sessionAdminService.revokeSession(actorId(authentication), userId, sessionRef, correlationId(request));
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }

    @PostMapping("/password")
    public ResponseEntity<Void> resetPassword(@PathVariable UUID userId,
                                              @Valid @RequestBody ResetPasswordRequest body,
                                              Authentication authentication,
                                              HttpServletRequest request) {
        sessionAdminService.resetPassword(actorId(authentication), userId, body.newPassword(),
                correlationId(request));
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }

    private UUID actorId(Authentication authentication) {
        return usersRepository.findByEmailIgnoreCase(authentication.getName())
                .map(user -> user.getUserId())
                .orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED, "Authentication is required."));
    }

    private static String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.CORRELATION_ID);
        return value == null ? null : value.toString();
    }
}
