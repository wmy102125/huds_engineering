package com.hugo.huds.user.service;

import com.hugo.huds.user.entity.SysUsers;
import com.hugo.huds.user.repository.SysUsersRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * @author meiyan wang
 * @date 2026-10-14 8:52 a.m.
 */
@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final SysUsersRepository sysUsersRepository;

    public DatabaseUserDetailsService(SysUsersRepository sysUsersRepository) {
        this.sysUsersRepository = sysUsersRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (!StringUtils.hasText(username)) {
            throw new IllegalArgumentException("Username can not be blank!");
        }
        SysUsers sysUsers = sysUsersRepository.findByEmailIgnoreCase(username).orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (!StringUtils.hasText(sysUsers.getPasswordHash())) {
            throw new UsernameNotFoundException("User not found");
        }
        return User.withUsername(sysUsers.getEmail())
                .password(sysUsers.getPasswordHash())
                .authorities("ROLE_" + sysUsers.getRole())
                .disabled(!Boolean.TRUE.equals(sysUsers.getActive())).build();
    }
}
