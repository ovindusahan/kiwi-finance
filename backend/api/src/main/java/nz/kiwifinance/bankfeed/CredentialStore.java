package nz.kiwifinance.bankfeed;

import java.nio.ByteBuffer;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nz.kiwifinance.common.crypto.CredentialCipher;
import nz.kiwifinance.common.crypto.EncryptedValue;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Encrypts provider credentials onto a connection. The connection ID is bound in as associated
 * data, so credentials cannot be moved between connections.
 */
@Component
@RequiredArgsConstructor
public class CredentialStore {

    private final CredentialCipher cipher;
    private final JsonMapper jsonMapper;

    EncryptedValue encrypt(UUID connectionId, Object credentials) {
        return cipher.encrypt(jsonMapper.writeValueAsBytes(credentials), associatedData(connectionId));
    }

    public <T> T read(BankConnection connection, Class<T> type) {
        EncryptedValue encrypted = connection.encryptedCredentials();
        if (encrypted == null) {
            throw new BankFeedException(BankFeedException.Reason.UNAUTHORISED, "Connection has no credentials");
        }
        return jsonMapper.readValue(cipher.decrypt(encrypted, associatedData(connection.getId())), type);
    }

    private static byte[] associatedData(UUID connectionId) {
        return ByteBuffer.allocate(16)
                .putLong(connectionId.getMostSignificantBits())
                .putLong(connectionId.getLeastSignificantBits())
                .array();
    }
}
