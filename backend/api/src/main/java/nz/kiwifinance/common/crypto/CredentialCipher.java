package nz.kiwifinance.common.crypto;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Encrypts third-party credentials with AES-256-GCM. Callers pass associated data, normally the
 * owning record's ID, so a ciphertext copied onto another record fails to decrypt.
 */
@Component
public class CredentialCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, SecretKey> keys = new HashMap<>();
    private final String activeKeyId;

    public CredentialCipher(CryptoProperties properties) {
        properties.keys().forEach((id, encoded) -> {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            if (bytes.length != KEY_BYTES) {
                throw new IllegalArgumentException("Encryption key '" + id + "' must be 32 bytes");
            }
            keys.put(id, new SecretKeySpec(bytes, "AES"));
        });
        this.activeKeyId = properties.activeKeyId();
    }

    public EncryptedValue encrypt(byte[] plaintext, byte[] associatedData) {
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keys.get(activeKeyId), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(associatedData);
            byte[] encrypted = cipher.doFinal(plaintext);
            return new EncryptedValue(
                    activeKeyId,
                    ByteBuffer.allocate(NONCE_BYTES + encrypted.length)
                            .put(nonce)
                            .put(encrypted)
                            .array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public byte[] decrypt(EncryptedValue value, byte[] associatedData) {
        SecretKey key = keys.get(value.keyId());
        if (key == null) {
            throw new IllegalStateException("Unknown encryption key '" + value.keyId() + "'");
        }
        ByteBuffer buffer = ByteBuffer.wrap(value.ciphertext());
        byte[] nonce = new byte[NONCE_BYTES];
        buffer.get(nonce);
        byte[] encrypted = new byte[buffer.remaining()];
        buffer.get(encrypted);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(associatedData);
            return cipher.doFinal(encrypted);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Decryption failed", e);
        }
    }
}
