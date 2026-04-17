package com.eaglepoint.console.service;

import com.eaglepoint.console.security.EncryptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Re-encrypts legacy plaintext values left in columns that are supposed to hold
 * AES-GCM ciphertext.
 *
 * <p>Flyway migrations (V2) seed user rows with human-readable markers
 * (e.g. {@code SEED-ADMIN}) in {@code users.staff_id_encrypted} so the SQL is
 * self-documenting.  The runtime contract however is that every value in that
 * column must be a valid AES-GCM blob produced by {@link EncryptionUtil}.  On
 * startup we scan the users table, attempt to decrypt each value, and re-encrypt
 * anything that fails decryption.  The operation is idempotent — once all rows
 * are ciphertext the scan is a no-op.</p>
 */
public class SeedEncryptionService {

    private static final Logger log = LoggerFactory.getLogger(SeedEncryptionService.class);

    private final DataSource ds;
    private final EncryptionUtil encryptionUtil;

    public SeedEncryptionService(DataSource ds, EncryptionUtil encryptionUtil) {
        this.ds = ds;
        this.encryptionUtil = encryptionUtil;
    }

    /** Encrypt any plaintext-like values found in users.staff_id_encrypted. */
    public int reencryptPlaintextStaffIds() {
        int updated = 0;
        try (Connection c = ds.getConnection();
             PreparedStatement select = c.prepareStatement(
                 "SELECT id, staff_id_encrypted FROM users WHERE staff_id_encrypted IS NOT NULL");
             ResultSet rs = select.executeQuery()) {

            while (rs.next()) {
                long id = rs.getLong("id");
                String value = rs.getString("staff_id_encrypted");
                if (value == null || value.isBlank()) continue;
                if (isValidCiphertext(value)) continue; // already encrypted

                String ciphertext = encryptionUtil.encrypt(value);
                try (PreparedStatement update = c.prepareStatement(
                    "UPDATE users SET staff_id_encrypted = ? WHERE id = ?")) {
                    update.setString(1, ciphertext);
                    update.setLong(2, id);
                    update.executeUpdate();
                    updated++;
                }
            }
            if (updated > 0) {
                log.info("Re-encrypted {} plaintext staff_id_encrypted row(s) on startup", updated);
            }
        } catch (Exception e) {
            log.error("Failed to re-encrypt seed staff ids: {}", e.getMessage(), e);
        }
        return updated;
    }

    private boolean isValidCiphertext(String value) {
        try {
            encryptionUtil.decrypt(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
