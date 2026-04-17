package com.eaglepoint.console.unit.ui;

import com.eaglepoint.console.ui.LoginInputValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link LoginInputValidator} used by {@code LoginDialog}.
 * The validator is a pure helper with no JavaFX dependency.
 */
class LoginInputValidatorTest {

    @Test
    void validCredentialsPass() {
        LoginInputValidator.Result r = LoginInputValidator.validate("admin", "Admin1234!");
        assertTrue(r.isValid());
        assertEquals("", r.message());
    }

    @Test
    void emptyUsernameFails() {
        LoginInputValidator.Result r = LoginInputValidator.validate("", "p@ss");
        assertFalse(r.isValid());
        assertTrue(r.message().toLowerCase().contains("username"));
    }

    @Test
    void whitespaceUsernameIsTreatedAsEmpty() {
        LoginInputValidator.Result r = LoginInputValidator.validate("   ", "p@ss");
        assertFalse(r.isValid());
    }

    @Test
    void emptyPasswordFails() {
        LoginInputValidator.Result r = LoginInputValidator.validate("admin", "");
        assertFalse(r.isValid());
        assertTrue(r.message().toLowerCase().contains("password"));
    }

    @Test
    void nullInputsFail() {
        assertFalse(LoginInputValidator.validate(null, "p").isValid());
        assertFalse(LoginInputValidator.validate("u", null).isValid());
        assertFalse(LoginInputValidator.validate(null, null).isValid());
    }

    @Test
    void overlongUsernameFails() {
        String longName = "a".repeat(LoginInputValidator.MAX_LENGTH + 1);
        LoginInputValidator.Result r = LoginInputValidator.validate(longName, "p@ss");
        assertFalse(r.isValid());
        assertTrue(r.message().toLowerCase().contains("long"));
    }

    @Test
    void overlongPasswordFails() {
        String longPw = "p".repeat(LoginInputValidator.MAX_LENGTH + 1);
        LoginInputValidator.Result r = LoginInputValidator.validate("admin", longPw);
        assertFalse(r.isValid());
    }

    @Test
    void maxLengthBoundaryPasses() {
        String maxName = "a".repeat(LoginInputValidator.MAX_LENGTH);
        String maxPw   = "p".repeat(LoginInputValidator.MAX_LENGTH);
        assertTrue(LoginInputValidator.validate(maxName, maxPw).isValid());
    }

    @Test
    void resultRecordShouldExposeValidAndMessage() {
        LoginInputValidator.Result ok = LoginInputValidator.Result.ok();
        assertTrue(ok.isValid());
        assertEquals("", ok.message());

        LoginInputValidator.Result bad = LoginInputValidator.Result.fail("boom");
        assertFalse(bad.isValid());
        assertEquals("boom", bad.message());
    }
}
