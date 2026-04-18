package com.eaglepoint.console.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.stream.Collectors;

/**
 * DPAPI-backed persistent AES-256 key storage for production Windows deployments.
 *
 * <p>Windows DPAPI (Data Protection API) ties the protected data to the current
 * Windows user account and machine. An attacker who obtains the raw key file cannot
 * decrypt it without also having access to the user's credential chain on the same
 * machine — unlike a plain keystore file or an in-memory key that is discarded on
 * restart.</p>
 *
 * <p>Java does not expose DPAPI through the standard JCA/JCE APIs.  This class
 * delegates to PowerShell's {@code System.Security.Cryptography.ProtectedData}
 * class (available on all modern Windows versions), which wraps the native
 * {@code CryptProtectData} / {@code CryptUnprotectData} Win32 calls.</p>
 *
 * <h3>Key lifecycle</h3>
 * <ol>
 *   <li>On first boot, a 32-byte random key is generated and DPAPI-protected,
 *       then written to {@code %APPDATA%\EaglePoint\Console\enc-key.bin}.</li>
 *   <li>On subsequent boots the file is read and DPAPI-unprotected to recover
 *       the original key bytes.</li>
 *   <li>Any failure (PowerShell not found, DPAPI error, corrupt file) throws
 *       {@link IllegalStateException}.  Production mode must NOT fall back to
 *       a fresh random key — doing so would make every previously-encrypted
 *       field unreadable after the restart.</li>
 * </ol>
 */
public class DpapiKeyStorage {
    private static final Logger log = LoggerFactory.getLogger(DpapiKeyStorage.class);

    private static final String KEY_FILE_NAME = "enc-key.bin";
    private static final String APP_DIR_RELATIVE = "EaglePoint\\Console";

    /**
     * Loads the AES-256 key from DPAPI-protected storage, creating and persisting
     * a new 32-byte key if none exists yet.
     *
     * @return 32-byte AES-256 key
     * @throws IllegalStateException if DPAPI protection, unprotection, or file I/O fails
     */
    public static byte[] loadOrCreate() {
        Path keyPath = resolveKeyPath();
        if (Files.exists(keyPath)) {
            log.info("Loading DPAPI-protected encryption key from {}", keyPath);
            return unprotect(keyPath);
        }
        log.info("No DPAPI key file found — generating new 32-byte key and protecting with DPAPI at {}", keyPath);
        byte[] newKey = new byte[32];
        new SecureRandom().nextBytes(newKey);
        protect(newKey, keyPath);
        log.info("DPAPI-protected encryption key stored at {}", keyPath);
        return newKey;
    }

    private static Path resolveKeyPath() {
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isEmpty()) {
            throw new IllegalStateException(
                "DPAPI key storage requires the %APPDATA% environment variable " +
                "(Windows user profile directory). Ensure the application runs as a " +
                "named Windows user, not as the SYSTEM account.");
        }
        Path dir = Paths.get(appData, APP_DIR_RELATIVE);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException(
                "Cannot create DPAPI key storage directory " + dir + ": " + e.getMessage(), e);
        }
        return dir.resolve(KEY_FILE_NAME);
    }

    /**
     * Reads the DPAPI-protected file and decrypts it using {@code ProtectedData.Unprotect}
     * (CurrentUser scope) via PowerShell.
     */
    private static byte[] unprotect(Path keyPath) {
        String safePath = keyPath.toString().replace("'", "''");
        String script =
            "Add-Type -AssemblyName System.Security; " +
            "$bytes = [System.IO.File]::ReadAllBytes('" + safePath + "'); " +
            "$plain = [System.Security.Cryptography.ProtectedData]::Unprotect(" +
            "$bytes, $null, [System.Security.Cryptography.DataProtectionScope]::CurrentUser); " +
            "[Convert]::ToBase64String($plain)";

        String b64 = runPowerShell(script, "DPAPI Unprotect");
        byte[] key;
        try {
            key = Base64.getDecoder().decode(b64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                "DPAPI key file returned malformed Base64 — the file may be corrupt: " + e.getMessage(), e);
        }
        if (key.length != 32) {
            throw new IllegalStateException(
                "DPAPI-stored key is corrupted: expected 32 bytes, got " + key.length +
                ". Delete " + keyPath + " and restart to generate a new key " +
                "(note: all previously-encrypted data will be unreadable).");
        }
        return key;
    }

    /**
     * Encrypts the raw key bytes using {@code ProtectedData.Protect} (CurrentUser scope)
     * and writes the ciphertext to disk.
     */
    private static void protect(byte[] key, Path keyPath) {
        String b64Key = Base64.getEncoder().encodeToString(key);
        String safePath = keyPath.toString().replace("'", "''");
        String script =
            "Add-Type -AssemblyName System.Security; " +
            "$plain = [Convert]::FromBase64String('" + b64Key + "'); " +
            "$protected = [System.Security.Cryptography.ProtectedData]::Protect(" +
            "$plain, $null, [System.Security.Cryptography.DataProtectionScope]::CurrentUser); " +
            "[System.IO.File]::WriteAllBytes('" + safePath + "', $protected)";

        runPowerShell(script, "DPAPI Protect");
    }

    /**
     * Executes a PowerShell command and returns stdout. Throws on non-zero exit.
     */
    private static String runPowerShell(String script, String opName) {
        ProcessBuilder pb = new ProcessBuilder(
            "powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script);
        pb.redirectErrorStream(false);
        try {
            Process proc = pb.start();

            String stdout;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                stdout = r.lines().collect(Collectors.joining("\n"));
            }
            String stderr;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getErrorStream()))) {
                stderr = r.lines().collect(Collectors.joining("\n"));
            }
            int exitCode = proc.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException(
                    opName + " failed (PowerShell exit " + exitCode + "): " + stderr.trim());
            }
            return stdout;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                "Cannot execute PowerShell for " + opName + ": " + e.getMessage(), e);
        }
    }
}
