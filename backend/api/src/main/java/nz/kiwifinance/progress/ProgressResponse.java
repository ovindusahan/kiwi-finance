package nz.kiwifinance.progress;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import nz.kiwifinance.engine.progress.Streaks;
import nz.kiwifinance.engine.score.KiwiScore;

@Schema(name = "Progress")
public record ProgressResponse(Score score, Streaks streaks, List<Achievement> achievements, int unlocked, int total) {

    @Schema(name = "KiwiScore")
    public record Score(
            int score,
            KiwiScore.Band band,
            String bandLabel,
            String summary,
            String nextStep,
            List<ScoreComponent> components) {

        static Score from(KiwiScore score) {
            return new Score(
                    score.score(),
                    score.band(),
                    score.band().label(),
                    score.summary(),
                    score.nextStep(),
                    score.components().stream()
                            .map(c ->
                                    new ScoreComponent(c.key(), c.title(), c.score(), c.weight(), c.detail(), c.tip()))
                            .toList());
        }
    }

    @Schema(name = "KiwiScoreComponent")
    public record ScoreComponent(String key, String title, int score, int weight, String detail, String tip) {}

    @Schema(name = "Achievement")
    public record Achievement(
            String key, String title, String description, String icon, boolean unlocked, BigDecimal progress) {}
}
