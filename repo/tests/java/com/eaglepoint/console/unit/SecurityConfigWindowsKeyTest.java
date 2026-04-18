package com.eaglepoint.console.unit;

import com.eaglepoint.console.config.SecurityConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SecurityConfig key-loading behaviour.
 *
 * <ul>
 *   <li>Env-var key (APP_TEST_ENC_KEY) takes priority and must be accepted.</li>
 *   <li>The headless / non-Windows path falls back to a fresh in-memory key.</li>
 *   <li>Key length must be exactly 32 bytes (AES-256).</li>
 *   <li>Production Windows mode delegates to DpapiKeyStorage and must NOT silently
 *       fall back to a random key when DPAPI fails.</li>
 * </ul>
 *
 * The singleton instance is reset between tests via reflection to ensure isolation.
 */
class SecurityConfigWindowsKeyTest {

    @AfterEach
    void resetSingleton() throws Exception {
        Field instanceField = SecurityConfig.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }

    @Test
    void envKeyIsUsedWhenPresent() throws Exception {
        byte[] expected = new byte[32];
        for (int i = 0; i < 32; i++) expected[i] = (byte) i;
        String b64 = Base64.getEncoder().encodeToString(expected);

        // Inject APP_TEST_ENC_KEY via system property (env vars can't be set in tests)
        System.setProperty("APP_TEST_ENC_KEY", b64);
        try {
            SecurityConfig sc = SecurityConfig.getInstance();
            invokeLoadKey(sc, true); // headless=true
            byte[] key = sc.getEncryptionKey();
            assertArrayEquals(expected, key, "Key from APP_TEST_ENC_KEY must match exactly");
        } finally {
            System.clearProperty("APP_TEST_ENC_KEY");
        }
    }

    @Test
    void envKeyMustBe32Bytes() throws Exception {
        // 16-byte key (too short for AES-256) should be ignored; falls back to in-memory key
        byte[] shortKey = new byte[16];
        String b64 = Base64.getEncoder().encodeToString(shortKey);
        System.setProperty("APP_TEST_ENC_KEY", b64);
        try {
            SecurityConfig sc = SecurityConfig.getInstance();
            invokeLoadKey(sc, true);
            byte[] key = sc.getEncryptionKey();
            // Should NOT equal the short key — the 16-byte candidate was rejected
            assertEquals(32, key.length, "Fallback in-memory key must still be 32 bytes");
            assertFalse(java.util.Arrays.equals(shortKey, java.util.Arrays.copyOf(key, 16)),
                "Should have fallen back to a fresh key, not accepted the invalid short key");
        } finally {
            System.clearProperty("APP_TEST_ENC_KEY");
        }
    }

    @Test
    void headlessModeGeneratesInMemoryKey() throws Exception {
        SecurityConfig sc = SecurityConfig.getInstance();
        invokeLoadKey(sc, true); // headless=true → generateInMemoryKey()
        byte[] key = sc.getEncryptionKey();
        assertNotNull(key, "Key must not be null in headless mode");
        assertEquals(32, key.length, "In-memory key must be 32 bytes (AES-256)");
    }

    @Test
    void malformedBase64EnvKeyIsSilentlySkipped() throws Exception {
        System.setProperty("APP_TEST_ENC_KEY", "NOT_VALID_BASE64!!!");
        try {
            SecurityConfig sc = SecurityConfig.getInstance();
            invokeLoadKey(sc, true);
            byte[] key = sc.getEncryptionKey();
            assertEquals(32, key.length, "Should have fallen back to in-memory 32-byte key");
        } finally {
            System.clearProperty("APP_TEST_ENC_KEY");
        }
    }

    @Test
    void productionWindowsModeFailsFastWhenDpapiUnavailable() throws Exception {
        // When running on a non-Windows OS (CI / Linux Docker) the isWindows() check
        // in loadKey() will be false, so loadFromDpapi() is never reached.
        // This test validates that the production guard is wired correctly: if we
        // forcibly invoke loadFromDpapi() without a valid DPAPI context, it must
        // throw rather than silently produce a random key.
        //
        // On Linux (where this test runs in CI), we cannot exercise the real
        // DpapiKeyStorage; instead we verify that loadFromDpapi() propagates the
        // IllegalStateException rather than swallowing it.
        SecurityConfig sc = SecurityConfig.getInstance();
        Method m = SecurityConfig.class.getDeclaredMethod("loadFromDpapi");
        m.setAccessible(true);
        try {
            m.invoke(sc);
            // On a real Windows machine with valid DPAPI this path succeeds —
            // assert the key is 32 bytes in that case.
            assertNotNull(sc.getEncryptionKey());
            assertEquals(32, sc.getEncryptionKey().length);
        } catch (java.lang.reflect.InvocationTargetException ite) {
            // Expected on Linux (no powershell.exe) or when DPAPI context is invalid.
            // The cause must be IllegalStateException, NOT NullPointerException or
            // a silent swallow that returns null.
            Throwable cause = ite.getCause();
            assertTrue(cause instanceof IllegalStateException,
                "Production DPAPI failure must propagate as IllegalStateException, got: "
                    + cause.getClass().getName());
        }
    }

    private void invokeLoadKey(SecurityConfig sc, boolean headless) throws Exception {
        Method m = SecurityConfig.class.getDeclaredMethod("loadKey", boolean.class);
        m.setAccessible(true);
        m.invoke(sc, headless);
    }
}
