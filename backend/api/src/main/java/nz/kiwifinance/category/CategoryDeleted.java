package nz.kiwifinance.category;

import java.util.UUID;

/**
 * Published inside the deleting transaction so features that reference the category can clear
 * those references before the row is removed.
 */
public record CategoryDeleted(UUID userId, UUID categoryId) {}
