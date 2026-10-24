package com.hugo.huds.user.service;

import com.hugo.huds.user.entity.Users;
import com.hugo.huds.user.repository.UsersRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Meiyan Wang
 * @date 2026-10-14 9:10 a.m.
 */
@ExtendWith(MockitoExtension.class)
public class DatabaseUserDetailsServiceTest {
    @Mock
    private UsersRepository usersRepository;
    @InjectMocks
    private DatabaseUserDetailsService databaseUserDetailsService;

    @Test
    public void findUserTest() {
        String userName = "test@example.com";
        Users users = new Users();
        users.setEmail(userName);
        users.setActive(true);
        users.setUserId(UUID.randomUUID());
        users.setRole("ADMIN");
        users.setUserName("test");
        users.setPasswordHash("2!xudfyduy6w3270");

        when(usersRepository.findByEmailIgnoreCase(userName)).thenReturn(Optional.of(users));

        UserDetails userDetails = databaseUserDetailsService.loadUserByUsername(userName);
        assertNotNull(userDetails);
        assertEquals("test@example.com", userDetails.getUsername());
        assertEquals(userDetails.getPassword(), "2!xudfyduy6w3270");
        assertTrue(userDetails.isEnabled());
        assertTrue(userDetails.getAuthorities().stream().anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals("ROLE_ADMIN")));

        verify(usersRepository).findByEmailIgnoreCase(userName);
    }

    @Test
    public void userNotFoundTest() {
        when(usersRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> databaseUserDetailsService.loadUserByUsername("test@example.com"));
    }

    @Test
    public void userDisabled() {
        String userName = "test@example.com";
        Users users = new Users();
        users.setEmail(userName);
        users.setActive(false);
        users.setUserId(UUID.randomUUID());
        users.setRole("ADMIN");
        users.setUserName("test");
        users.setPasswordHash("2!xudfyduy6w3270");
        when(usersRepository.findByEmailIgnoreCase(userName)).thenReturn(Optional.of(users));

        UserDetails userDetails = databaseUserDetailsService.loadUserByUsername(userName);
        assertFalse(userDetails.isEnabled());
    }

    @Test
    public void userPasswordNull() {
        String userName = "test@example.com";
        Users users = new Users();
        users.setEmail(userName);
        users.setActive(false);
        users.setUserId(UUID.randomUUID());
        users.setRole("ADMIN");
        users.setUserName("test");

        when(usersRepository.findByEmailIgnoreCase(userName)).thenReturn(Optional.of(users));
        assertThrows(UsernameNotFoundException.class, () -> databaseUserDetailsService.loadUserByUsername(userName));
    }


}
