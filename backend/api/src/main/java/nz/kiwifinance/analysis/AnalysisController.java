package nz.kiwifinance.analysis;

import lombok.RequiredArgsConstructor;
import nz.kiwifinance.common.web.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analysis")
@RequiredArgsConstructor
class AnalysisController {

    private final AnalysisService analysisService;

    @GetMapping("/cashflow")
    AnalysisResponses.Cashflow cashflow(CurrentUser user, @RequestParam(defaultValue = "12") int months) {
        return analysisService.cashflow(user.id(), months);
    }

    @GetMapping("/spending")
    AnalysisResponses.Spending spending(CurrentUser user, @RequestParam(defaultValue = "3") int months) {
        return analysisService.spending(user.id(), months);
    }

    @GetMapping("/recurring")
    AnalysisResponses.RecurringSummary recurring(CurrentUser user) {
        return analysisService.recurring(user.id());
    }
}
