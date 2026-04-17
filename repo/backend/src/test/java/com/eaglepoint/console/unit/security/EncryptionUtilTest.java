package com.eaglepoint.console.unit.security;

import com.eaglepoint.console.security.EncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import static org.junit.jupiter.api.Assertions.*;

class EncryptionUtilTest {

    private EncryptionUtil encryptionUtil;

    @BeforeEach
    void setUp() throws Exception {
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(256);
        SecretKey key = kg.generateKey();
        encryptionUtil = new EncryptionUtil(key);
    }

    @Test
    void encryptAndDecryptRoundTrip() throws Exception {
        String plaintext = "sensitive-data-12345";
        String ciphertext = encryptionUtil.encrypt(plaintext);
        assertNotNull(ciphertext);
        assertNotEquals(plaintext, ciphertext);
        String decrypted = encryptionUtil.decrypt(ciphertext);
        assertEquals(plaintext, decrypted);
    }

    @Test
    void encryptProducesDifferentCiphertextsForSameInput() throws Exception {
        String plaintext = "same-input";
        String ct1 = encryptionUtil.encrypt(plaintext);
        String ct2 = encryptionUtil.encrypt(plaintext);
        // Due to random IV, each encryption should produce a different ciphertext
        assertNotEquals(ct1, ct2);
    }

    @Test
    void encryptNullThrowsOrHandlesGracefully() {
        assertThrows(Exception.class, () -> encryptionUtil.encrypt(null));
    }

    @Test
    void decryptInvalidCiphertextThrows() {
        assertThrows(Exception.class, () -> encryptionUtil.decrypt("not-valid-ciphertext"));
    }

    @Test
    void encryptEmptyString() throws Exception {
        String plaintext = "";
        String ciphertext = encryptionUtil.encrypt(plaintext);
        assertNotNull(ciphertext);
        String decrypted = encryptionUtil.decrypt(ciphertext);
        assertEquals(plaintext, decrypted);
    }

    @Test
    void encryptLongString() throws Exception {
        String plaintext = "A".repeat(10000);
        String ciphertext = encryptionUtil.encrypt(plaintext);
        String decrypted = encryptionUtil.decrypt(ciphertext);
        assertEquals(plaintext, decrypted);
    }
}
