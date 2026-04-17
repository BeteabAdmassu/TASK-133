package com.eaglepoint.console.service;

import com.eaglepoint.console.exception.UnauthorizedException;
import com.eaglepoint.console.exception.ValidationException;
import com.eaglepoint.console.model.ApiToken;
import com.eaglepoint.console.model.User;
import com.eaglepoint.console.repository.ApiTokenRepository;
import com.eaglepoint.console.repository.UserRepository;
import com.eaglepoint.console.security.PasswordUtil;
import com.eaglepoint.console.security.TokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private final UserRepository userRepo;
    private final ApiTokenRepository tokenRepo;
    private final TokenService tokenService;

    public AuthService(UserRepository userRepo, ApiTokenRepository tokenRepo, TokenService tokenService) {
        this.userRepo = userRepo;
        this.tokenRepo = tokenRepo;
        this.tokenService = tokenService;
    }

    public Map<String, Object> login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new ValidationException("username", "Username and password are required");
        }

        User user = userRepo.findByUsername(username)
            .orElseThrow(() -> new UnauthorizedException("Invalid username or password"));

        if (!user.isActive()) {
            throw new UnauthorizedException("Invalid username or password");
        }

        if (!PasswordUtil.verifyPassword(password, user.getPasswordHash())) {
            log.warn("Failed login attempt for username: {}", username);
            throw new UnauthorizedException("Invalid username or password");
        }

        // Revoke existing tokens
        tokenRepo.deleteByUserId(user.getId());

        // Issue new token
        String rawToken = tokenService.generateToken();
        String tokenHash = tokenService.hashToken(rawToken);
        String expiresAt = Instant.now().plus(24, ChronoUnit.HOURS).toString();

        ApiToken token = new ApiToken();
        token.setUserId(user.getId());
        token.setTokenHash(tokenHash);
        token.setExpiresAt(expiresAt);
        tokenRepo.insert(token);

        userRepo.updateLastLogin(user.getId(), Instant.now().toString());

        log.info("User logged in: {}", username);

        Map<String, Object> result = new HashMap<>();
        result.put("user", user);
        result.put("token", rawToken);
        result.put("expiresAt", expiresAt);
        return result;
    }

    public void logout(String tokenHash) {
        ApiToken token = tokenRepo.findByTokenHash(tokenHash).orElse(null);
        if (token != null) {
            tokenRepo.deleteByUserId(token.getUserId());
        }
    }

    public User validateToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new UnauthorizedException("Missing authorization token");
        }

        String tokenHash = tokenService.hashToken(rawToken);
        ApiToken token = tokenRepo.findByTokenHash(tokenHash)
            .orElseThrow(() -> new UnauthorizedException("Invalid or expired token"));

        if (tokenService.isExpired(token.getExpiresAt())) {
            tokenRepo.deleteByUserId(token.getUserId());
            throw new UnauthorizedException("Token has expired");
        }

        return userRepo.findById(token.getUserId())
            .orElseThrow(() -> new UnauthorizedException("User not found"));
    }
}
