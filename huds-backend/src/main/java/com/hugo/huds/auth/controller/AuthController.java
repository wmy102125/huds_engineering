package com.hugo.huds.auth.controller;

import com.hugo.huds.auth.dto.CsrfResponse;
import com.hugo.huds.auth.dto.LoginRequest;
import com.hugo.huds.auth.dto.LoginResponse;
import com.hugo.huds.auth.dto.MeResponse;
import com.hugo.huds.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author meiyan wang
 * @date 2026-10-14 10:14 p.m.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }
    /** Returns the CSRF token (also set as the XSRF-TOKEN cookie). Call before login and after a CSRF 403. */
    @GetMapping("/csrf")
    public ResponseEntity<CsrfResponse> csrf(CsrfToken token) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new CsrfResponse(token.getHeaderName(), token.getToken()));
    }
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest,
                                               HttpServletRequest request,
                                               HttpServletResponse response) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(authService.login(loginRequest, request, response));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore())
                .build();
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(Authentication authentication, HttpSession session) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(authService.me(authentication, session));
    }
}
