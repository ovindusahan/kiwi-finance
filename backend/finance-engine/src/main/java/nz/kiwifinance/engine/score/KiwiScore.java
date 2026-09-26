package nz.kiwifinance.engine.score;

import java.util.List;

/**
 * A 0 to 100 measure of financial wellbeing, with the parts that make it up and what would
 * improve each.
 */
public record KiwiScore(int score, Band band, List<Component> components, String summary, String nextStep) {

    public KiwiScore {
        components = List.copyOf(components);
    }

    public enum Band {
        GETTING_STARTED("Getting started"),
        BUILDING("Building"),
        SOLID("Solid"),
        THRIVING("Thriving");

        private final String label;

        Band(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        static Band of(int score) {
            if (score >= 80) {
                return THRIVING;
            }
            if (score >= 60) {
                return SOLID;
            }
            return score >= 40 ? BUILDING : GETTING_STARTED;
        }
    }

    /**
     * @param weight how much this part counts towards the total, out of 100
     */
    public record Component(String key, String title, int score, int weight, String detail, String tip) {}
}
