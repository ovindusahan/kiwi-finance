package nz.kiwifinance.auth;

import java.time.Instant;
import java.util.UUID;

record TokenPair(
        String accessToken,
        Instant accessTokenExpiresAt,
        UUID refreshTokenId,
        String refreshToken,
        Instant refreshTokenExpiresAt) {}
