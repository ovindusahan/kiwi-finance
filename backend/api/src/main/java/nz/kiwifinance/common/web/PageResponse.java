package nz.kiwifinance.common.web;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A page of results. {@code nextCursor} is opaque and {@code null} on the last page.
 */
public record PageResponse<T>(List<T> items, @Nullable String nextCursor) {}
