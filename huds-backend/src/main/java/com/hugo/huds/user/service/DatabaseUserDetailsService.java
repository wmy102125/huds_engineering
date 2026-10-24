package com.hugo.huds.user.service;

import com.hugo.huds.auth.support.HudsUserDetails;
import com.hugo.huds.auth.support.RolePermissions;
import com.hugo.huds.user.entity.Users;
import com.hugo.huds.user.repository.UsersRepository;
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

        //get the role and permissions for the user and add them to the authorities list
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + users.getRole()));
        RolePermissions.getPermissionsByRole(users.getRole()).forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));

        //check if the user is locked by checking if the lockedUntil field is not null and if it is after the current time
        boolean locked = users.getLockedUntil() != null && users.getLockedUntil().isAfter(Instant.now());

        return new HudsUserDetails(
                users.getUserId(),
                users.getEmail(),
                users.getPasswordHash(),
                Boolean.TRUE.equals(users.getActive()),
                !locked,
                authorities);
    }
}
