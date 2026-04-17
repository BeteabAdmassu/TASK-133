package com.eaglepoint.console.service;

import com.eaglepoint.console.config.SecurityConfig;
import com.eaglepoint.console.exception.ConflictException;
import com.eaglepoint.console.exception.NotFoundException;
import com.eaglepoint.console.exception.ValidationException;
import com.eaglepoint.console.model.PagedResult;
import com.eaglepoint.console.model.User;
import com.eaglepoint.console.repository.UserRepository;
import com.eaglepoint.console.security.EncryptionUtil;
import com.eaglepoint.console.security.PasswordUtil;

import java.util.Set;
import java.util.regex.Pattern;

public class UserService {
    private static final Set<String> VALID_ROLES = Set.of(
        "SYSTEM_ADMIN", "OPS_MANAGER", "REVIEWER", "AUDITOR", "DATA_INTEGRATOR"
    );
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,50}$");

    private final UserRepository userRepo;
    private final EncryptionUtil encryptionUtil;

    public UserService(UserRepository userRepo, SecurityConfig securityConfig) {
        this.userRepo = userRepo;
        this.encryptionUtil = new EncryptionUtil(securityConfig.getEncryptionKey());
    }

    public User createUser(String username, String password, String displayName, String role, String staffId) {
        validateUsername(username);
        validatePassword(password);
        validateDisplayName(displayName);
        validateRole(role);
        validateStaffId(staffId);

        if (userRepo.findByUsername(username).isPresent()) {
            throw new ConflictException("A user with username '" + username + "' already exists");
        }

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(PasswordUtil.hashPassword(password));
        user.setDisplayName(displayName.trim());
        user.setRole(role);
        user.setStaffIdEncrypted(encryptionUtil.encrypt(staffId));
        user.setActive(true);

        long id = userRepo.insert(user);
        return userRepo.findById(id).orElseThrow(() -> new RuntimeException("Failed to retrieve created user"));
    }

    public User getUser(long id) {
        return userRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("User", id));
    }

    public PagedResult<User> listUsers(int page, int pageSize) {
        return userRepo.findAll(page, pageSize);
    }

    public User updateUser(long id, String displayName, String role, Boolean isActive, String staffId) {
        User user = userRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("User", id));

        if (displayName != null) {
            validateDisplayName(displayName);
            user.setDisplayName(displayName.trim());
        }
        if (role != null) {
            validateRole(role);
            user.setRole(role);
        }
        if (isActive != null) {
            user.setActive(isActive);
        }
        if (staffId != null) {
            validateStaffId(staffId);
            user.setStaffIdEncrypted(encryptionUtil.encrypt(staffId));
        }

        userRepo.update(user);
        return userRepo.findById(id).orElseThrow();
    }

    public void deactivateUser(long id) {
        if (userRepo.findById(id).isEmpty()) {
            throw new NotFoundException("User", id);
        }
        userRepo.deactivate(id);
    }

    private void validateUsername(String username) {
        if (username == null || !USERNAME_PATTERN.matcher(username).matches()) {
            throw new ValidationException("username", "Username must be 3-50 characters, alphanumeric and underscore only");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 128) {
            throw new ValidationException("password", "Password must be 8-128 characters");
        }
    }

    private void validateDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank() || displayName.trim().length() > 100) {
            throw new ValidationException("displayName", "Display name must be 1-100 characters");
        }
    }

    private void validateRole(String role) {
        if (!VALID_ROLES.contains(role)) {
            throw new ValidationException("role", "Invalid role. Must be one of: " + VALID_ROLES);
        }
    }

    private void validateStaffId(String staffId) {
        if (staffId == null || staffId.isBlank() || staffId.length() > 50) {
            throw new ValidationException("staffId", "Staff ID must be 1-50 characters");
        }
    }
}
