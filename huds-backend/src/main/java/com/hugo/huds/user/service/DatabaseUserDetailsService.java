package com.hugo.huds.user.service;

import com.hugo.huds.user.entity.Users;
import com.hugo.huds.user.repository.UsersRepository;
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

    private final UsersRepository usersRepository;

    public DatabaseUserDetailsService(UsersRepository usersRepository) {
        this.usersRepository = usersRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (!StringUtils.hasText(username)) {
            throw new IllegalArgumentException("Username can not be blank!");
        }
        Users users = usersRepository.findByEmailIgnoreCase(username).orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (!StringUtils.hasText(users.getPasswordHash())) {
            throw new UsernameNotFoundException("User not found");
        }
        return User.withUsername(users.getEmail())
                .password(users.getPasswordHash())
                .authorities("ROLE_" + users.getRole())
                .disabled(!Boolean.TRUE.equals(users.getActive())).build();
    }
}
