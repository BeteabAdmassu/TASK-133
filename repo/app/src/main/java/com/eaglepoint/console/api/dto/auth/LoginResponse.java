package com.eaglepoint.console.api.dto.auth;

import com.eaglepoint.console.model.User;

public class LoginResponse {
    private UserSummary user;
    private String token;
    private String expiresAt;

    public LoginResponse(User user, String token, String expiresAt) {
        this.user = new UserSummary(user.getId(), user.getUsername(), user.getRole(), user.getDisplayName());
        this.token = token;
        this.expiresAt = expiresAt;
    }

    public static class UserSummary {
        private long id;
        private String username;
        private String role;
        private String displayName;

        public UserSummary(long id, String username, String role, String displayName) {
            this.id = id;
            this.username = username;
            this.role = role;
            this.displayName = displayName;
        }

        public long getId() { return id; }
        public String getUsername() { return username; }
        public String getRole() { return role; }
        public String getDisplayName() { return displayName; }
    }

    public UserSummary getUser() { return user; }
    public String getToken() { return token; }
    public String getExpiresAt() { return expiresAt; }
}
