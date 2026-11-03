package com.hugo.huds.user.service;

import com.hugo.huds.auth.support.HudsUserDetails;
import com.hugo.huds.auth.support.RolePermissions;
import com.hugo.huds.user.entity.User;
import com.hugo.huds.user.repository.UserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Meiyan Wang
 * @date 2026-10-14 8:52 a.m.
 */
@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public DatabaseUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (!StringUtils.hasText(username)) {
            throw new IllegalArgumentException("Username can not be blank!");
        }
        User user = userRepository.findByEmailIgnoreCase(username).orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (!StringUtils.hasText(user.getPasswordHash())) {
            throw new UsernameNotFoundException("User not found");
        }

        //get the role and permissions for the user and add them to the authorities list
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole()));
        RolePermissions.getPermissionsByRole(user.getRole()).forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));

        //check if the user is locked by checking if the lockedUntil field is not null and if it is after the current time
        boolean locked = user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now());

        return new HudsUserDetails(
                user.getUserId(),
                user.getEmail(),
                user.getPasswordHash(),
                Boolean.TRUE.equals(user.getActive()),
                !locked,
                authorities);
    }
}
