package com.eaglepoint.console.unit.service;

import com.eaglepoint.console.exception.UnauthorizedException;
import com.eaglepoint.console.model.ApiToken;
import com.eaglepoint.console.model.User;
import com.eaglepoint.console.repository.ApiTokenRepository;
import com.eaglepoint.console.repository.UserRepository;
import com.eaglepoint.console.security.TokenService;
import com.eaglepoint.console.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that AuthService rejects tokens belonging to a deactivated user
 * (even if the token itself has not yet expired) and revokes them on first
 * contact so subsequent requests fail immediately with 401.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceDeactivationTest {

    @Mock private UserRepository userRepo;
    @Mock private ApiTokenRepository tokenRepo;
    @Mock private TokenService tokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepo, tokenRepo, tokenService);
    }

    @Test
    void validateTokenRejectsDeactivatedUserAndRevokesToken() {
        String rawToken = "raw-token-xyz";
        String hash = "hash-xyz";

        ApiToken token = new ApiToken();
        token.setUserId(7L);
        token.setTokenHash(hash);
        token.setExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS).toString());

        User user = new User();
        user.setId(7L);
        user.setUsername("ghost");
        user.setActive(false); // deactivated after token was issued

        when(tokenService.hashToken(rawToken)).thenReturn(hash);
        when(tokenRepo.findByTokenHash(hash)).thenReturn(Optional.of(token));
        when(tokenService.isExpired(anyString())).thenReturn(false);
        when(userRepo.findById(7L)).thenReturn(Optional.of(user));

        UnauthorizedException e = assertThrows(UnauthorizedException.class,
            () -> authService.validateToken(rawToken));
        assertTrue(e.getMessage().toLowerCase().contains("deactivat"));
        verify(tokenRepo).deleteByUserId(7L); // eager revocation
    }

    @Test
    void validateTokenAcceptsActiveUser() {
        String rawToken = "good-token";
        String hash = "good-hash";

        ApiToken token = new ApiToken();
        token.setUserId(3L);
        token.setTokenHash(hash);
        token.setExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS).toString());

        User user = new User();
        user.setId(3L);
        user.setActive(true);

        when(tokenService.hashToken(rawToken)).thenReturn(hash);
        when(tokenRepo.findByTokenHash(hash)).thenReturn(Optional.of(token));
        when(tokenService.isExpired(anyString())).thenReturn(false);
        when(userRepo.findById(3L)).thenReturn(Optional.of(user));

        assertEquals(user, authService.validateToken(rawToken));
        verify(tokenRepo, never()).deleteByUserId(3L);
    }
}
