package nz.kiwifinance.common.persistence;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;

/**
 * Generates time-ordered UUIDv7 identifiers (RFC 9562), which keep B-tree indexes compact and
 * give a stable creation order.
 */
public final class Ids {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Clock CLOCK = Clock.systemUTC();

    private Ids() {}

    public static UUID newId() {
        long millis = CLOCK.millis();
        long randA = RANDOM.nextInt(1 << 12);
        long mostSignificant = (millis << 16) | (0x7L << 12) | randA;
        long leastSignificant = (RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(mostSignificant, leastSignificant);
    }
}
