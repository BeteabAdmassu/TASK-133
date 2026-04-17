package com.eaglepoint.console.unit.service;

import com.eaglepoint.console.exception.UnauthorizedException;
import com.eaglepoint.console.model.ApiToken;
import com.eaglepoint.console.model.User;
import com.eaglepoint.console.repository.ApiTokenRepository;
import com.eaglepoint.console.repository.UserRepository;
import com.eaglepoint.console.security.PasswordUtil;
import com.eaglepoint.console.security.TokenService;
import com.eaglepoint.console.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepo;
    @Mock private ApiTokenRepository tokenRepo;
    @Mock private TokenService tokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepo, tokenRepo, tokenService);
    }

    @Test
    void loginSucceedsWithCorrectCredentials() {
        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        user.setPasswordHash(PasswordUtil.hashPassword("secret"));
        user.setRole("SYSTEM_ADMIN");
        user.setActive(true);

        when(userRepo.findByUsername("admin")).thenReturn(Optional.of(user));
        when(tokenService.generateToken()).thenReturn("raw-token-abc");
        when(tokenService.hashToken("raw-token-abc")).thenReturn("hash-abc");
        doNothing().when(tokenRepo).deleteByUserId(1L);
        when(tokenRepo.insert(any(ApiToken.class))).thenReturn(1L);
        doNothing().when(userRepo).updateLastLogin(anyLong(), anyString());

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) authService.login("admin", "secret");
        assertNotNull(result);
        assertNotNull(result.get("token"));
        assertEquals(user, result.get("user"));
    }

    @Test
    void loginFailsWithWrongPassword() {
        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        user.setPasswordHash(PasswordUtil.hashPassword("secret"));
        user.setActive(true);

        when(userRepo.findByUsername("admin")).thenReturn(Optional.of(user));

        assertThrows(UnauthorizedException.class, () -> authService.login("admin", "wrong"));
    }

    @Test
    void loginFailsForNonExistentUser() {
        when(userRepo.findByUsername("nobody")).thenReturn(Optional.empty());
        assertThrows(UnauthorizedException.class, () -> authService.login("nobody", "pass"));
    }

    @Test
    void loginFailsForInactiveUser() {
        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        user.setPasswordHash(PasswordUtil.hashPassword("secret"));
        user.setActive(false);

        when(userRepo.findByUsername("admin")).thenReturn(Optional.of(user));
        assertThrows(UnauthorizedException.class, () -> authService.login("admin", "secret"));
    }

    @Test
    void validateTokenSucceeds() {
        String rawToken = "some-raw-token";
        String hash = "hashed-token";

        ApiToken token = new ApiToken();
        token.setId(1L);
        token.setUserId(2L);
        token.setTokenHash(hash);
        token.setExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS).toString());

        User user = new User();
        user.setId(2L);
        user.setActive(true);

        when(tokenService.hashToken(rawToken)).thenReturn(hash);
        when(tokenRepo.findByTokenHash(hash)).thenReturn(Optional.of(token));
        when(tokenService.isExpired(anyString())).thenReturn(false);
        when(userRepo.findById(2L)).thenReturn(Optional.of(user));

        User result = authService.validateToken(rawToken);
        assertEquals(user, result);
    }

    @Test
    void validateTokenFailsWhenExpired() {
        String rawToken = "expired-token";
        String hash = "expired-hash";

        ApiToken token = new ApiToken();
        token.setId(1L);
        token.setUserId(2L);
        token.setTokenHash(hash);
        token.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS).toString());

        when(tokenService.hashToken(rawToken)).thenReturn(hash);
        when(tokenRepo.findByTokenHash(hash)).thenReturn(Optional.of(token));
        when(tokenService.isExpired(anyString())).thenReturn(true);
        doNothing().when(tokenRepo).deleteByUserId(anyLong());

        assertThrows(UnauthorizedException.class, () -> authService.validateToken(rawToken));
    }

    @Test
    void validateTokenFailsWhenTokenNotFound() {
        when(tokenService.hashToken(anyString())).thenReturn("some-hash");
        when(tokenRepo.findByTokenHash(anyString())).thenReturn(Optional.empty());
        assertThrows(UnauthorizedException.class, () -> authService.validateToken("nonexistent"));
    }
}
