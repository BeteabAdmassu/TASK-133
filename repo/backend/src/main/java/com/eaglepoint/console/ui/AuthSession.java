package com.eaglepoint.console.ui;

import com.eaglepoint.console.model.User;

import java.util.Optional;

public class AuthSession {

    private static AuthSession instance;
    private User currentUser;
    private String rawToken;

    private AuthSession() {}

    public static synchronized AuthSession getInstance() {
        if (instance == null) {
            instance = new AuthSession();
        }
        return instance;
    }

    public void set(User user, String rawToken) {
        this.currentUser = user;
        this.rawToken = rawToken;
    }

    public void clear() {
        this.currentUser = null;
        this.rawToken = null;
    }

    public Optional<User> getCurrentUser() {
        return Optional.ofNullable(currentUser);
    }

    public Optional<String> getRawToken() {
        return Optional.ofNullable(rawToken);
    }

    public boolean isLoggedIn() {
        return currentUser != null && rawToken != null;
    }
}
