package com.hugo.huds.auth.support;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.io.Serial;
import java.util.Collection;
import java.util.UUID;

/**
 * @author Meiyan Wang
 * @date 2026-10-19 11:07 p.m.
 */
@Getter
public class HudsUserDetails extends User {
    @Serial
    private static final long serialVersionUID = 1L;
    private final UUID userId;

    public HudsUserDetails(UUID userId, String username, String password, boolean enabled,
                           boolean accountNonLocked, Collection<? extends GrantedAuthority> authorities) {
        super(username, password, enabled, true, true, accountNonLocked, authorities);
        this.userId = userId;
    }
}
