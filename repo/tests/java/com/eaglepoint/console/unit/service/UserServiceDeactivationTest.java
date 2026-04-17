package com.eaglepoint.console.unit.service;

import com.eaglepoint.console.config.SecurityConfig;
import com.eaglepoint.console.model.User;
import com.eaglepoint.console.repository.ApiTokenRepository;
import com.eaglepoint.console.repository.UserRepository;
import com.eaglepoint.console.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * UserService must revoke outstanding bearer tokens whenever a user
 * transitions ACTIVE -> INACTIVE, whether via {@code deactivateUser} or via
 * {@code updateUser(..., isActive=false, ...)}.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceDeactivationTest {

    @Mock private UserRepository userRepo;
    @Mock private ApiTokenRepository tokenRepo;

    private UserService userService;

    @BeforeEach
    void setUp() {
        SecurityConfig.initHeadless();
        userService = new UserService(userRepo, tokenRepo, SecurityConfig.getInstance());
    }

    @Test
    void deactivateUserRevokesTokens() {
        User existing = new User();
        existing.setId(12L);
        existing.setActive(true);
        when(userRepo.findById(12L)).thenReturn(Optional.of(existing));

        userService.deactivateUser(12L);

        verify(userRepo).deactivate(12L);
        verify(tokenRepo).deleteByUserId(12L);
    }

    @Test
    void updateUserSettingInactiveRevokesTokens() {
        User existing = new User();
        existing.setId(15L);
        existing.setActive(true);
        existing.setDisplayName("Before");
        existing.setRole("OPS_MANAGER");
        when(userRepo.findById(15L)).thenReturn(Optional.of(existing));

        userService.updateUser(15L, null, null, Boolean.FALSE, null);

        verify(userRepo).update(any());
        verify(tokenRepo).deleteByUserId(15L);
    }

    @Test
    void updateUserKeepingActiveDoesNotRevokeTokens() {
        User existing = new User();
        existing.setId(16L);
        existing.setActive(true);
        existing.setDisplayName("Before");
        existing.setRole("OPS_MANAGER");
        when(userRepo.findById(16L)).thenReturn(Optional.of(existing));

        userService.updateUser(16L, "After", null, Boolean.TRUE, null);

        verify(userRepo).update(any());
        verify(tokenRepo, never()).deleteByUserId(16L);
    }
}
