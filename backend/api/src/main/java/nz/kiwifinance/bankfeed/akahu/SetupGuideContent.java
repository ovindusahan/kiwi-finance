package nz.kiwifinance.bankfeed.akahu;

import java.util.List;
import java.util.Map;
import nz.kiwifinance.bankfeed.ConnectionMethod;

/**
 * The words of the Akahu setup guide, loaded from {@code bankfeed/akahu-setup-guide.json}.
 */
record SetupGuideContent(
        Map<ConnectionMethod, Method> methods,
        Map<String, String> attention,
        List<SetupGuide.Note> security,
        List<SetupGuide.Faq> troubleshooting,
        List<String> supportedBanks,
        String supportedBanksNote) {

    record Method(String title, String summary, int estimatedMinutes, List<String> requirements, List<Step> steps) {}

    record Step(String id, String title, String body, String tip, List<SetupGuide.Link> links) {}
}
