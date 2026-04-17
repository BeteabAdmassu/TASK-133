package com.eaglepoint.console.ui;

/**
 * Pure input validation for the login dialog — extracted from
 * {@link LoginDialog} so it can be unit-tested without a JavaFX runtime.
 */
public final class LoginInputValidator {

    /** Maximum length accepted for either field.  Keeps the UI paste-safe. */
    public static final int MAX_LENGTH = 256;

    private LoginInputValidator() {}

    /**
     * Result of a validation attempt.  If {@link #isValid()} is {@code false}
     * then {@link #message()} contains a user-facing error string ready to be
     * displayed in the login dialog; otherwise {@code message()} is empty.
     */
    public record Result(boolean valid, String message) {
        public boolean isValid() { return valid; }
        public static Result ok() { return new Result(true, ""); }
        public static Result fail(String msg) { return new Result(false, msg); }
    }

    /**
     * Validate the raw username/password strings as entered by the user.
     * Returns an {@link Result#ok()} if both are non-blank and within the
     * length limit, otherwise a failure with a human-readable message.
     */
    public static Result validate(String username, String password) {
        String u = username == null ? "" : username.trim();
        String p = password == null ? "" : password;
        if (u.isEmpty() || p.isEmpty()) {
            return Result.fail("Please enter username and password.");
        }
        if (u.length() > MAX_LENGTH || p.length() > MAX_LENGTH) {
            return Result.fail("Username or password is too long.");
        }
        return Result.ok();
    }
}
