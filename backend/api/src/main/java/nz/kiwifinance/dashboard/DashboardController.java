package nz.kiwifinance.dashboard;

import java.util.List;
import lombok.RequiredArgsConstructor;
import nz.kiwifinance.common.web.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/api/v1/dashboard")
    DashboardResponse dashboard(CurrentUser user) {
        return dashboardService.dashboard(user.id());
    }

    @GetMapping("/api/v1/insights")
    List<InsightResponse> insights(CurrentUser user) {
        return dashboardService.insights(user.id());
    }
}
