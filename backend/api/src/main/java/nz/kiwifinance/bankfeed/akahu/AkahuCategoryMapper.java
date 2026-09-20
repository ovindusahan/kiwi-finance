package nz.kiwifinance.bankfeed.akahu;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps Akahu's merchant enrichment to Kiwi Finance system categories. Akahu category names follow
 * the NZ Financial Category Codes, so keywords in the name identify the category reliably enough
 * for a starting point; people can always recategorise, and their own rules take precedence.
 */
final class AkahuCategoryMapper {

    private record Mapping(List<String> keywords, String slug) {

        static Mapping of(String slug, String... keywords) {
            return new Mapping(List.of(keywords), slug);
        }
    }

    // Order matters: more specific keywords come before broader ones.
    private static final List<Mapping> BY_NAME = List.of(
            Mapping.of("takeaways", "takeaway", "fast food"),
            Mapping.of("groceries", "supermarket", "grocer", "grocery"),
            Mapping.of("eating-out", "cafe", "coffee", "restaurant", "bar", "bars", "pub", "pubs", "dining"),
            Mapping.of("fuel", "fuel", "petrol", "service station", "service stations"),
            Mapping.of("public-transport", "public transport", "bus", "train", "ferry", "taxi", "rideshare"),
            Mapping.of("vehicle", "parking", "automotive", "vehicle", "mechanic", "car repair", "tyre", "toll"),
            Mapping.of("power-gas", "electricity", "electric", "gas", "power"),
            Mapping.of("internet-phone", "telecommunication", "telecommunications", "internet", "phone", "mobile"),
            Mapping.of("insurance", "insurance"),
            Mapping.of(
                    "health",
                    "pharmacy",
                    "pharmacies",
                    "medical",
                    "doctor",
                    "doctors",
                    "dental",
                    "dentist",
                    "health",
                    "hospital",
                    "optometrist"),
            Mapping.of("childcare", "childcare", "early childhood", "daycare"),
            Mapping.of("education", "education", "school", "schools", "tuition", "university"),
            Mapping.of("subscriptions", "streaming", "subscription", "subscriptions", "software"),
            Mapping.of("clothing", "clothing", "apparel", "footwear", "shoe", "shoes"),
            Mapping.of(
                    "travel", "travel", "airline", "airlines", "accommodation", "hotel", "hotels", "motel", "lodging"),
            Mapping.of("personal-care", "hair", "hairdresser", "beauty", "cosmetic", "cosmetics", "barber"),
            Mapping.of(
                    "entertainment",
                    "entertainment",
                    "cinema",
                    "cinemas",
                    "recreation",
                    "gaming",
                    "sport",
                    "sports",
                    "event",
                    "events"),
            Mapping.of("gifts-donations", "gift", "gifts", "charity", "charities", "donation", "donations"),
            Mapping.of("rates", "council", "rates"),
            Mapping.of(
                    "shopping",
                    "department store",
                    "department stores",
                    "retail",
                    "electronics",
                    "homeware",
                    "hardware",
                    "furniture"));

    private static final Map<String, String> BY_GROUP = Map.of(
            "education", "education",
            "health", "health");

    private AkahuCategoryMapper() {}

    /**
     * The system category slug for an Akahu transaction, or {@code null} to leave it uncategorised.
     */
    static String slugFor(AkahuModels.Transaction transaction) {
        String type = transaction.type() == null ? "" : transaction.type().toUpperCase(Locale.ROOT);
        switch (type) {
            case "TRANSFER" -> {
                return "transfers";
            }
            case "INTEREST" -> {
                return transaction.amount() != null && transaction.amount().signum() > 0
                        ? "interest"
                        : "debt-repayments";
            }
            case "FEE" -> {
                return "bank-fees";
            }
            default -> {}
        }
        AkahuModels.Category category = transaction.category();
        if (category == null) {
            return null;
        }
        if (category.name() != null) {
            String[] words = normalise(category.name());
            for (Mapping mapping : BY_NAME) {
                if (mapping.keywords().stream().anyMatch(keyword -> contains(words, keyword))) {
                    return mapping.slug();
                }
            }
        }
        if (category.groups() != null && category.groups().personalFinance() != null) {
            String group = category.groups().personalFinance().name();
            return group == null ? null : BY_GROUP.get(group.toLowerCase(Locale.ROOT));
        }
        return null;
    }

    private static String[] normalise(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z]+", " ").trim().split(" ");
    }

    private static boolean contains(String[] words, String keyword) {
        String[] parts = keyword.split(" ");
        for (int i = 0; i + parts.length <= words.length; i++) {
            boolean match = true;
            for (int j = 0; j < parts.length && match; j++) {
                match = sameWord(words[i + j], parts[j]);
            }
            if (match) {
                return true;
            }
        }
        return false;
    }

    /**
     * Matches a word or its plural, so "cafe" matches "Cafes" but "bus" does not match "business".
     */
    private static boolean sameWord(String word, String keyword) {
        return word.equals(keyword) || word.equals(keyword + "s") || word.equals(keyword + "es");
    }
}
