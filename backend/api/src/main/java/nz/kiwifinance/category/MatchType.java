package nz.kiwifinance.category;

import java.util.Locale;

public enum MatchType {
    CONTAINS,
    STARTS_WITH,
    EQUALS;

    boolean matches(String text, String pattern) {
        String haystack = text.toLowerCase(Locale.ROOT).trim();
        String needle = pattern.toLowerCase(Locale.ROOT).trim();
        return switch (this) {
            case CONTAINS -> haystack.contains(needle);
            case STARTS_WITH -> haystack.startsWith(needle);
            case EQUALS -> haystack.equals(needle);
        };
    }
}
