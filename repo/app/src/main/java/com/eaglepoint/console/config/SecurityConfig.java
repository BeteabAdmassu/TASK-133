package com.eaglepoint.console.config;

import java.security.SecureRandom;
import java.util.Base64;

public class SecurityConfig {
    private static SecurityConfig instance;
    private byte[] encryptionKey;

    private SecurityConfig() {}

    public static synchronized SecurityConfig getInstance() {
        if (instance == null) instance = new SecurityConfig();
        return instance;
    }

    public static void init() {
        getInstance().loadKey(false);
    }

    public static void initHeadless() {
        getInstance().loadKey(true);
    }

    private void loadKey(boolean headless) {
        // Priority 1 & 2: accept a pre-supplied key from the environment or
        // a JVM system property (used by Docker/CI and test harnesses).
        // Both must decode to exactly 32 bytes (AES-256); invalid values are
        // skipped so the next source is tried.
        for (String candidate : new String[] {
                System.getenv("APP_TEST_ENC_KEY"),
                System.getProperty("APP_TEST_ENC_KEY") }) {
            if (candidate == null || candidate.isEmpty()) continue;
            try {
                byte[] decoded = Base64.getDecoder().decode(candidate);
                if (decoded.length == 32) {
                    this.encryptionKey = decoded;
                    return;
                }
            } catch (IllegalArgumentException ignored) {
                // Malformed Base64 — try next source.
            }
        }

        if (!headless && isWindows()) {
            // Production Windows path: use DPAPI-backed persistent key storage.
            // DpapiKeyStorage.loadOrCreate() throws IllegalStateException on any
            // failure (PowerShell unavailable, DPAPI error, corrupt file, etc.).
            // We deliberately do NOT catch that exception here; production mode
            // must fail fast rather than silently generate an ephemeral key that
            // would make all previously-encrypted fields unreadable after a restart.
            loadFromDpapi();
        } else {
            // Headless / Docker / test mode — generate a session-scoped random key.
            // In these environments the APP_TEST_ENC_KEY env var should normally
            // provide a stable key; this fallback is only for unit tests that do
            // not set that variable.
            generateInMemoryKey();
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /**
     * Loads the AES-256 encryption key from DPAPI-backed file storage.
     *
     * <p>This replaces the previous Windows-MY (certificate store) approach, which
     * does not apply Windows DPAPI protection. DPAPI binds the key to the Windows
     * user identity so it is unreadable outside this machine/user context without
     * the full credential chain.</p>
     *
     * <p>Any failure propagates as {@link IllegalStateException} — there is no
     * silent fallback to a fresh random key in production mode.</p>
     */
    private void loadFromDpapi() {
        this.encryptionKey = DpapiKeyStorage.loadOrCreate();
    }

    private void generateInMemoryKey() {
        this.encryptionKey = new byte[32];
        new SecureRandom().nextBytes(this.encryptionKey);
    }

    public byte[] getEncryptionKey() {
        if (encryptionKey == null) {
            loadKey(AppConfig.getInstance().isHeadless());
        }
        return encryptionKey;
    }
}
