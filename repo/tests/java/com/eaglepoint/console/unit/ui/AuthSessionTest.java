package com.eaglepoint.console.unit.ui;

import com.eaglepoint.console.model.User;
import com.eaglepoint.console.ui.AuthSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the JavaFX desktop {@link AuthSession} singleton — the
 * non-network state holder used by the UI to track the logged-in user.
 *
 * <p>These tests do not boot a JavaFX runtime; {@code AuthSession} is a pure
 * Java class with no JavaFX imports.</p>
 */
class AuthSessionTest {

    private AuthSession session;

    @BeforeEach
    void setUp() {
        session = AuthSession.getInstance();
        // Ensure each test starts from a clean slate — the class is a JVM-wide singleton.
        session.clear();
    }

    @Test
    void cleanSessionIsNotLoggedIn() {
        assertFalse(session.isLoggedIn());
        assertTrue(session.getCurrentUser().isEmpty());
        assertTrue(session.getRawToken().isEmpty());
    }

    @Test
    void setMarksSessionLoggedInAndExposesUserAndToken() {
        User user = new User();
        user.setId(42L);
        user.setUsername("admin");
        user.setRole("SYSTEM_ADMIN");

        session.set(user, "raw-token-xyz");

        assertTrue(session.isLoggedIn());
        assertEquals(user, session.getCurrentUser().orElseThrow());
        assertEquals("raw-token-xyz", session.getRawToken().orElseThrow());
    }

    @Test
    void clearResetsBothUserAndToken() {
        User user = new User();
        user.setId(1L);
        session.set(user, "tok");
        assertTrue(session.isLoggedIn());

        session.clear();

        assertFalse(session.isLoggedIn());
        assertTrue(session.getCurrentUser().isEmpty());
        assertTrue(session.getRawToken().isEmpty());
    }

    @Test
    void setWithNullUserLeavesSessionIncomplete() {
        session.set(null, "tok");
        // isLoggedIn requires BOTH user and token — null user should fail the gate.
        assertFalse(session.isLoggedIn());
        assertTrue(session.getCurrentUser().isEmpty());
    }

    @Test
    void setWithNullTokenLeavesSessionIncomplete() {
        User user = new User();
        user.setId(1L);
        session.set(user, null);
        assertFalse(session.isLoggedIn());
        assertTrue(session.getRawToken().isEmpty());
    }

    @Test
    void getInstanceAlwaysReturnsSameSingleton() {
        assertSame(AuthSession.getInstance(), AuthSession.getInstance());
    }
}
