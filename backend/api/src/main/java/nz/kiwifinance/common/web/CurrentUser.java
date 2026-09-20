package nz.kiwifinance.common.web;

import java.util.UUID;

/**
 * The authenticated user, injected into controller methods.
 */
public record CurrentUser(UUID id) {}
