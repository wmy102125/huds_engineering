package com.hugo.huds.config;

import com.hugo.huds.auth.support.HudsUserDetails;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * @author Meiyan Wang
 * @date 2026-10-19 11:20 p.m.
 * @description Supplies the id of the current user to JPA(CreatedBy and ModifiedBy).
 * Returns empty when nobody is authenticated, so the NOT NULL column fails loudly instead of silently recording a wrong user.
 *
 */
@Component
public class CurrentUserAuditorAware implements AuditorAware<UUID> {
    @Override
    public Optional<UUID> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        if (authentication.getPrincipal() instanceof HudsUserDetails details) {
            return Optional.of(details.getUserId());
        }
        return Optional.empty();
    }
}
