package com.hugo.huds.user.service;

import com.hugo.huds.user.entity.SysUsers;
import com.hugo.huds.user.repository.SysUsersRepository;
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
 * @author meiyan wang
 * @date 2026-10-14 9:10 a.m.
 */
@ExtendWith(MockitoExtension.class)
public class DatabaseUserDetailsServiceTest {
    @Mock
    private SysUsersRepository sysUsersRepository;
    @InjectMocks
    private DatabaseUserDetailsService databaseUserDetailsService;

    @Test
    public void findUserTest() {
        String userName = "test@example.com";
        SysUsers sysUsers = new SysUsers();
        sysUsers.setEmail(userName);
        sysUsers.setActive(true);
        sysUsers.setId(UUID.randomUUID());
        sysUsers.setRole("ADMIN");
        sysUsers.setDisplayName("test");
        sysUsers.setPasswordHash("2!xudfyduy6w3270");

        when(sysUsersRepository.findByEmailIgnoreCase(userName)).thenReturn(Optional.of(sysUsers));

        UserDetails userDetails = databaseUserDetailsService.loadUserByUsername(userName);
        assertNotNull(userDetails);
        assertEquals("test@example.com", userDetails.getUsername());
        assertEquals(userDetails.getPassword(), "2!xudfyduy6w3270");
        assertTrue(userDetails.isEnabled());
        assertTrue(userDetails.getAuthorities().stream().anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals("ROLE_ADMIN")));

        verify(sysUsersRepository).findByEmailIgnoreCase(userName);
    }

    @Test
    public void userNotFoundTest() {
        when(sysUsersRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> databaseUserDetailsService.loadUserByUsername("test@example.com"));
    }

    @Test
    public void userDisabled() {
        String userName = "test@example.com";
        SysUsers sysUsers = new SysUsers();
        sysUsers.setEmail(userName);
        sysUsers.setActive(false);
        sysUsers.setId(UUID.randomUUID());
        sysUsers.setRole("ADMIN");
        sysUsers.setDisplayName("test");
        sysUsers.setPasswordHash("2!xudfyduy6w3270");
        when(sysUsersRepository.findByEmailIgnoreCase(userName)).thenReturn(Optional.of(sysUsers));

        UserDetails userDetails = databaseUserDetailsService.loadUserByUsername(userName);
        assertFalse(userDetails.isEnabled());
    }

    @Test
    public void userPasswordNull() {
        String userName = "test@example.com";
        SysUsers sysUsers = new SysUsers();
        sysUsers.setEmail(userName);
        sysUsers.setActive(false);
        sysUsers.setId(UUID.randomUUID());
        sysUsers.setRole("ADMIN");
        sysUsers.setDisplayName("test");

        when(sysUsersRepository.findByEmailIgnoreCase(userName)).thenReturn(Optional.of(sysUsers));
        assertThrows(UsernameNotFoundException.class, () -> databaseUserDetailsService.loadUserByUsername(userName));
    }


}
