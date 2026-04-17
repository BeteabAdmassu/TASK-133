package com.eaglepoint.console.security;

import org.mindrot.jbcrypt.BCrypt;

public class PasswordUtil {
    private static final int COST_FACTOR = 12;

    public static String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(COST_FACTOR));
    }

    public static boolean verifyPassword(String password, String hash) {
        if (password == null || hash == null) return false;
        return BCrypt.checkpw(password, hash);
    }
}
