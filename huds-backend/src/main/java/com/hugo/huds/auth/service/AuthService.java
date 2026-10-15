package com.hugo.huds.auth.service;

import com.hugo.huds.auth.dto.LoginRequest;
import com.hugo.huds.auth.dto.LoginResponse;
import com.hugo.huds.user.entity.Users;
import com.hugo.huds.user.repository.UsersRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author meiyan wang
 * @date 2026-10-14 10:43 p.m.
 */
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UsersRepository usersRepository;

    public AuthService(AuthenticationManager authenticationManager, UsersRepository usersRepository) {
        this.authenticationManager = authenticationManager;
        this.usersRepository = usersRepository;
    }
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest loginRequest) {

        Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(loginRequest.email().trim(), loginRequest.password()));

        Users user = usersRepository.findByEmailIgnoreCase(authentication.getName()).orElseThrow(() -> new IllegalStateException(loginRequest.email() + "authenticated user could not be found"));

        return new LoginResponse(user.getUserId(), user.getUserName(), user.getEmail(), user.getRole());
    }

}
