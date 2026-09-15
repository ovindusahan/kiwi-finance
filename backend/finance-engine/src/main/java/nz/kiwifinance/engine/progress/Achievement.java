package nz.kiwifinance.engine.progress;

import java.math.BigDecimal;

/**
 * A milestone worth celebrating.
 *
 * @param progress how close the person is, from 0 to 1
 */
public record Achievement(
        String key, String title, String description, String icon, boolean unlocked, BigDecimal progress) {}
