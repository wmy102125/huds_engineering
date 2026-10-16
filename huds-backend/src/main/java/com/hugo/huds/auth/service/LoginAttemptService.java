package com.hugo.huds.auth.service;

import com.hugo.huds.config.AuthProperties;
import com.hugo.huds.user.repository.UsersRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * @author meiyan wang
 * @date 2026-10-16 11:11 a.m.
 * Failed-login counting and temporary lockout. State lives in the database, not in memory, so it
 * survives restarts and works with more than one application instance.
 */
@Service
public class LoginAttemptService {

    /** Result of recording a failure; {@code lockedUntil} is set only when this failure triggered a lock. */
    public record FailureResult(boolean lockedNow, Instant lockedUntil) {
    }
    private final UsersRepository usersRepository;
    private final AuthProperties properties;
    private final Clock clock;

    public LoginAttemptService(UsersRepository usersRepository, AuthProperties properties) {
        this.usersRepository = usersRepository;
        this.properties = properties;
        this.clock = Clock.systemUTC();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FailureResult registerFailure(UUID userId) {
        usersRepository.incrementFailedLoginAttempts(userId);
        int attmpts = usersRepository.findById(userId).map(u -> u.getFailedLoginAttempts()).orElse(0);
        if (attmpts >= properties.maxFailedAttempts()) {
            Instant until = clock.instant().plus(properties.lockDuration());
            usersRepository.lockUntil(userId, until);
            return new FailureResult(true, until);
        }
        return new FailureResult(false, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registerSuccess(UUID userId) {
        usersRepository.clearFailures(userId);
    }
}
