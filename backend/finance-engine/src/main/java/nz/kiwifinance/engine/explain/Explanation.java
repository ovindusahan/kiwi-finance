package nz.kiwifinance.engine.explain;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The working behind a result, written for someone who is not a financial expert. Clients render
 * it as "How we worked this out".
 */
public record Explanation(String summary, List<Step> steps, List<Assumption> assumptions) {

    public Explanation {
        steps = List.copyOf(steps);
        assumptions = List.copyOf(assumptions);
    }

    public record Step(String label, String value, @Nullable String detail) {}

    public record Assumption(
            String key,
            String label,
            String value,
            @Nullable String source) {}

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String summary = "";
        private final List<Step> steps = new ArrayList<>();
        private final List<Assumption> assumptions = new ArrayList<>();

        private Builder() {}

        public Builder summary(String summary) {
            this.summary = summary;
            return this;
        }

        public Builder step(String label, String value) {
            return step(label, value, null);
        }

        public Builder step(String label, String value, String detail) {
            steps.add(new Step(label, value, detail));
            return this;
        }

        public Builder assumption(String key, String label, String value, String source) {
            assumptions.add(new Assumption(key, label, value, source));
            return this;
        }

        public Builder assumptions(List<Assumption> more) {
            assumptions.addAll(more);
            return this;
        }

        public Explanation build() {
            return new Explanation(summary, steps, assumptions);
        }
    }
}
