package com.eaglepoint.console.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Verifies detached Ed25519 signatures over update-package manifests.
 *
 * <p>The signing trust material is a base64-encoded
 * {@link java.security.spec.X509EncodedKeySpec X.509 SPKI} public key (the
 * format produced by {@code openssl pkey -pubout}), loaded from one of:
 * <ol>
 *   <li>{@code UPDATER_PUBLIC_KEY} environment variable (base64 SPKI)</li>
 *   <li>System property {@code updater.public.key} (base64 SPKI)</li>
 *   <li>File at {@code updater.public.key.file} / {@code UPDATER_PUBLIC_KEY_FILE}</li>
 *   <li>Default file {@code data/updater/trust.pem.pub} alongside the DB</li>
 * </ol>
 *
 * <p>The verifier is intentionally read-only and side-effect-free; callers
 * receive a {@link Result} describing whether the signature matches the
 * provided manifest bytes.  No logging of the signature itself or of the
 * private-key material ever occurs — we log the key id / status only.</p>
 */
public class UpdateSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(UpdateSignatureVerifier.class);
    private static final String ED25519 = "Ed25519";

    private final PublicKey trustedKey;
    private final String keySource;

    public UpdateSignatureVerifier(PublicKey trustedKey, String keySource) {
        this.trustedKey = trustedKey;
        this.keySource = keySource;
    }

    public static UpdateSignatureVerifier load() {
        String env = firstNonBlank(
            System.getenv("UPDATER_PUBLIC_KEY"),
            System.getProperty("updater.public.key"));
        if (env != null) {
            return fromBase64Spki(env, "env:UPDATER_PUBLIC_KEY");
        }
        String file = firstNonBlank(
            System.getenv("UPDATER_PUBLIC_KEY_FILE"),
            System.getProperty("updater.public.key.file"));
        if (file == null) {
            file = "data/updater/trust.pem.pub";
        }
        Path p = Path.of(file);
        if (Files.exists(p)) {
            try {
                String encoded = stripPem(Files.readString(p));
                return fromBase64Spki(encoded, "file:" + p.toAbsolutePath());
            } catch (Exception e) {
                log.warn("Updater trust key file {} could not be loaded: {}", p, e.getMessage());
            }
        }
        // No trust material found — verifier in "untrusted" mode: every
        // verification will fail so callers cannot silently accept unsigned
        // packages in production.
        return new UpdateSignatureVerifier(null, "none");
    }

    /**
     * Verify a detached Ed25519 signature over {@code manifestBytes}.
     *
     * @param manifestBytes raw {@code manifest.json} content
     * @param signatureBytes detached signature bytes (raw — not base64)
     */
    public Result verify(byte[] manifestBytes, byte[] signatureBytes) {
        if (manifestBytes == null || manifestBytes.length == 0) {
            return Result.failure("UNTRUSTED", "manifest is empty");
        }
        if (signatureBytes == null || signatureBytes.length == 0) {
            return Result.failure("INVALID", "signature is empty");
        }
        if (trustedKey == null) {
            return Result.failure("UNTRUSTED",
                "no updater trust key loaded — set UPDATER_PUBLIC_KEY or provide data/updater/trust.pem.pub");
        }
        try {
            Signature verifier = Signature.getInstance(ED25519);
            verifier.initVerify(trustedKey);
            verifier.update(manifestBytes);
            boolean ok = verifier.verify(signatureBytes);
            if (ok) {
                return Result.success(keySource);
            }
            return Result.failure("INVALID", "Ed25519 signature did not match manifest");
        } catch (Exception e) {
            return Result.failure("INVALID", "Ed25519 verification threw: " + e.getClass().getSimpleName());
        }
    }

    public boolean isConfigured() {
        return trustedKey != null;
    }

    public String getKeySource() {
        return keySource;
    }

    private static UpdateSignatureVerifier fromBase64Spki(String base64Spki, String source) {
        try {
            byte[] spki = Base64.getDecoder().decode(base64Spki.trim());
            PublicKey key = KeyFactory.getInstance(ED25519)
                .generatePublic(new X509EncodedKeySpec(spki));
            return new UpdateSignatureVerifier(key, source);
        } catch (Exception e) {
            log.warn("Rejecting updater trust key from {}: {}", source, e.getMessage());
            return new UpdateSignatureVerifier(null, source + " (invalid)");
        }
    }

    private static String stripPem(String pem) {
        return pem.replaceAll("-----BEGIN [^-]+-----", "")
            .replaceAll("-----END [^-]+-----", "")
            .replaceAll("\\s", "");
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    public static final class Result {
        private final boolean valid;
        /** One of {@code VALID}, {@code INVALID}, {@code UNTRUSTED}. */
        private final String status;
        private final String reason;

        private Result(boolean valid, String status, String reason) {
            this.valid = valid;
            this.status = status;
            this.reason = reason;
        }

        public static Result success(String reason) { return new Result(true, "VALID", reason); }
        public static Result failure(String status, String reason) { return new Result(false, status, reason); }

        public boolean isValid() { return valid; }
        public String getStatus() { return status; }
        public String getReason() { return reason; }
    }
}
