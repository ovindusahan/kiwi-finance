package nz.kiwifinance.common.crypto;

/**
 * @param ciphertext the 12-byte nonce followed by the AES-GCM ciphertext and tag
 */
public record EncryptedValue(String keyId, byte[] ciphertext) {}
