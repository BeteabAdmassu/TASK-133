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
        // On Linux/headless: use env var or generate in-memory key
        String testKey = System.getenv("APP_TEST_ENC_KEY");
        if (testKey != null && !testKey.isEmpty()) {
            byte[] decoded = Base64.getDecoder().decode(testKey);
            if (decoded.length == 32) {
                this.encryptionKey = decoded;
                return;
            }
        }

        if (!headless && isWindows()) {
            loadFromWindowsKeyStore();
        } else {
            // Generate or use a fixed in-memory key for headless/test mode
            generateInMemoryKey();
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private void loadFromWindowsKeyStore() {
        try {
            java.security.KeyStore ks = java.security.KeyStore.getInstance("Windows-MY");
            ks.load(null, null);
            String alias = "eaglepoint-console-enc-key";
            if (ks.containsAlias(alias)) {
                java.security.Key key = ks.getKey(alias, null);
                if (key != null) {
                    this.encryptionKey = key.getEncoded();
                    return;
                }
            }
            // Generate and store new key
            byte[] newKey = new byte[32];
            new SecureRandom().nextBytes(newKey);
            javax.crypto.spec.SecretKeySpec secretKey = new javax.crypto.spec.SecretKeySpec(newKey, "AES");
            ks.setKeyEntry(alias, secretKey, null, null);
            this.encryptionKey = newKey;
        } catch (Exception e) {
            // Fallback to in-memory key if Windows keystore unavailable
            generateInMemoryKey();
        }
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
