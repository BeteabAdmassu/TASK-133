package com.eaglepoint.console.unit.security;

import com.eaglepoint.console.security.EncryptionUtil;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Static-audit compliance for V2__seed_admin_user.sql.
 *
 * <p>The seed migration must not persist plaintext markers in encrypted
 * columns.  These tests read the migration file from the classpath and
 * assert both the absence of plaintext markers and — as a round-trip
 * confidence check — that each documented ciphertext decrypts back to its
 * expected value under the pinned seed key.</p>
 */
class SeedEncryptionComplianceTest {

    /** 32-byte seed key used to generate the ciphertext baked into V2. */
    private static final byte[] SEED_KEY =
        "eaglepoint-console-seed-key-v1!!".getBytes(StandardCharsets.UTF_8);

    private static final List<String> PLAINTEXT_MARKERS = List.of(
        "SEED-ADMIN", "SEED-OPS", "SEED-REV", "SEED-AUD", "SEED-DI");

    private String readMigration() throws Exception {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("db/migrations/V2__seed_admin_user.sql")) {
            assertNotNull(in, "V2__seed_admin_user.sql must be on the classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void migrationFileContainsNoPlaintextSeedMarkersInColumnValues() throws Exception {
        String sql = readMigration();

        // Strip SQL line comments so the human-facing documentation of the
        // markers inside /* */ or `--` lines does not trigger the check —
        // what matters is the INSERT value position.
        StringBuilder stripped = new StringBuilder();
        for (String line : sql.split("\\R")) {
            String trimmed = line.stripLeading();
            if (trimmed.startsWith("--")) continue;
            stripped.append(line).append('\n');
        }
        String code = stripped.toString();

        for (String marker : PLAINTEXT_MARKERS) {
            String quoted = "'" + marker + "'";
            assertFalse(code.contains(quoted),
                "Plaintext seed marker " + quoted + " must not appear as a SQL value in V2 migration");
        }
    }

    @Test
    void seededCiphertextRoundTripsBackToExpectedMarkers() {
        EncryptionUtil util = new EncryptionUtil(SEED_KEY);
        // Mapping is documented in the migration's leading comment.
        assertEquals("SEED-ADMIN", util.decrypt("K+epFq+MYbOJBRGS6NIq3V/bCO6gs6MPLknRKVuK7bsYg8fS5/w="));
        assertEquals("SEED-OPS",   util.decrypt("fjYPWr7lw26i8t9N6o+U6ecDk1cIfFZhAAQGWLJ3qPEGZpa6"));
        assertEquals("SEED-REV",   util.decrypt("opsFWyGpuNMRErt4vXlfXcBIL1rZmiosm1KZfwZAqI7Zld0v"));
        assertEquals("SEED-AUD",   util.decrypt("g0kvrUwWM989lVc02O4IIkWHJA07IOf0XohS3fO+/2VJB6pX"));
        assertEquals("SEED-DI",    util.decrypt("s/vNcjXN4hRKJ0SIyihPgWLVgf9qOs5cbxVSTHdQ4MqPwUc="));
    }

    @Test
    void migrationCiphertextColumnsDecodeAsBase64() throws Exception {
        String sql = readMigration();
        // Pull each quoted token, look for the staff_id_encrypted column position:
        // each VALUES row ends `..., '<ciphertext>', 1),`
        // An AES-GCM Base64 blob is at least 24 chars; we just check the five
        // literal ciphertext strings from the migration.
        String[] expected = {
            "K+epFq+MYbOJBRGS6NIq3V/bCO6gs6MPLknRKVuK7bsYg8fS5/w=",
            "fjYPWr7lw26i8t9N6o+U6ecDk1cIfFZhAAQGWLJ3qPEGZpa6",
            "opsFWyGpuNMRErt4vXlfXcBIL1rZmiosm1KZfwZAqI7Zld0v",
            "g0kvrUwWM989lVc02O4IIkWHJA07IOf0XohS3fO+/2VJB6pX",
            "s/vNcjXN4hRKJ0SIyihPgWLVgf9qOs5cbxVSTHdQ4MqPwUc="
        };
        for (String ct : expected) {
            assertTrue(sql.contains("'" + ct + "'"),
                "Expected ciphertext " + ct + " in V2 migration");
            byte[] raw = Base64.getDecoder().decode(ct);
            assertTrue(raw.length > 12, // 12-byte IV prefix minimum
                "Ciphertext " + ct + " must be a well-formed AES-GCM payload");
        }
    }

    @Test
    void seedEncryptionServiceStillPresentAsBackwardSafety() {
        // The runtime fallback must remain available for deployments whose
        // AES key differs from the seed key.  Loading the class by name
        // guards against accidental removal.
        assertDoesNotThrow(() ->
            Class.forName("com.eaglepoint.console.service.SeedEncryptionService"));
    }
}
