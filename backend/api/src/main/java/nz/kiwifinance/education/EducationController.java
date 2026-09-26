package nz.kiwifinance.education;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import nz.kiwifinance.common.error.ApiException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

/**
 * Plain-English guides and an NZ money glossary. Public, because they help people before they
 * sign up too.
 */
@RestController
@RequestMapping("/api/v1/education")
class EducationController {

    private final EducationContent content;

    EducationController(JsonMapper jsonMapper) throws IOException {
        try (InputStream input = new ClassPathResource("education/content.json").getInputStream()) {
            this.content = jsonMapper.readValue(input, EducationContent.class);
        }
    }

    @GetMapping("/guides")
    List<EducationContent.GuideSummary> guides() {
        return content.guides().stream()
                .map(EducationContent.GuideSummary::from)
                .toList();
    }

    @GetMapping("/guides/{slug}")
    EducationContent.Guide guide(@PathVariable String slug) {
        return content.guides().stream()
                .filter(guide -> guide.slug().equals(slug))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Guide"));
    }

    @GetMapping("/glossary")
    List<EducationContent.GlossaryEntry> glossary() {
        return content.glossary();
    }
}
