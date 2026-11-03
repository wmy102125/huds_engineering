package com.hugo.huds.user.service;

import com.hugo.huds.user.entity.User;
import com.hugo.huds.user.repository.UserRepository;
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
    private UserRepository userRepository;
    @InjectMocks
    private DatabaseUserDetailsService databaseUserDetailsService;

    @Test
    public void findUserTest() {
        String userName = "test@example.com";
        User user = new User();
        user.setEmail(userName);
        user.setActive(true);
        user.setUserId(UUID.randomUUID());
        user.setRole("ADMIN");
        user.setUserName("test");
        user.setPasswordHash("2!xudfyduy6w3270");

        when(userRepository.findByEmailIgnoreCase(userName)).thenReturn(Optional.of(user));

        UserDetails userDetails = databaseUserDetailsService.loadUserByUsername(userName);
        assertNotNull(userDetails);
        assertEquals("test@example.com", userDetails.getUsername());
        assertEquals(userDetails.getPassword(), "2!xudfyduy6w3270");
        assertTrue(userDetails.isEnabled());
        assertTrue(userDetails.getAuthorities().stream().anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals("ROLE_ADMIN")));

        verify(userRepository).findByEmailIgnoreCase(userName);
    }

    @Test
    public void userNotFoundTest() {
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> databaseUserDetailsService.loadUserByUsername("test@example.com"));
    }

    @Test
    public void userDisabled() {
        String userName = "test@example.com";
        User user = new User();
        user.setEmail(userName);
        user.setActive(false);
        user.setUserId(UUID.randomUUID());
        user.setRole("ADMIN");
        user.setUserName("test");
        user.setPasswordHash("2!xudfyduy6w3270");
        when(userRepository.findByEmailIgnoreCase(userName)).thenReturn(Optional.of(user));

        UserDetails userDetails = databaseUserDetailsService.loadUserByUsername(userName);
        assertFalse(userDetails.isEnabled());
    }

    @Test
    public void userPasswordNull() {
        String userName = "test@example.com";
        User user = new User();
        user.setEmail(userName);
        user.setActive(false);
        user.setUserId(UUID.randomUUID());
        user.setRole("ADMIN");
        user.setUserName("test");

        when(userRepository.findByEmailIgnoreCase(userName)).thenReturn(Optional.of(user));
        assertThrows(UsernameNotFoundException.class, () -> databaseUserDetailsService.loadUserByUsername(userName));
    }


}
