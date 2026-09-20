package nz.kiwifinance.auth;

import java.util.UUID;

/**
 * Published before a user's rows are deleted, so features holding third-party access (such as
 * bank feeds) can revoke it first. Database rows are removed by cascading foreign keys.
 */
public record UserDeletionRequested(UUID userId) {}
