package nz.kiwifinance.education;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

record EducationContent(List<Guide> guides, List<GlossaryEntry> glossary) {

    @Schema(name = "Guide")
    record Guide(String slug, String title, String summary, String topic, int readingMinutes, List<Section> sections) {}

    @Schema(name = "GuideSection")
    record Section(String heading, String body) {}

    @Schema(name = "GlossaryEntry")
    record GlossaryEntry(String term, String definition) {}

    @Schema(name = "GuideSummary")
    record GuideSummary(String slug, String title, String summary, String topic, int readingMinutes) {

        static GuideSummary from(Guide guide) {
            return new GuideSummary(
                    guide.slug(), guide.title(), guide.summary(), guide.topic(), guide.readingMinutes());
        }
    }
}
