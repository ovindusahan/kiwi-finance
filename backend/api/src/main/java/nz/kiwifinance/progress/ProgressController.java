package nz.kiwifinance.progress;

import lombok.RequiredArgsConstructor;
import nz.kiwifinance.common.web.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class ProgressController {

    private final ProgressService progressService;

    @GetMapping("/api/v1/progress")
    ProgressResponse progress(CurrentUser user) {
        return progressService.forUser(user.id());
    }
}
