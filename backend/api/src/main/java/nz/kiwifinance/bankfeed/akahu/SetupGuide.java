package nz.kiwifinance.bankfeed.akahu;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import nz.kiwifinance.bankfeed.ConnectionMethod;
import nz.kiwifinance.bankfeed.ConnectionStatus;
import nz.kiwifinance.bankfeed.SyncStatus;
import org.jspecify.annotations.Nullable;

/**
 * A setup guide tailored to one person: which ways to connect are available, and where they are
 * up to in each. Clients render it as a step-by-step wizard.
 */
@Schema(name = "AkahuSetupGuide")
public record SetupGuide(
        State state,
        String headline,
        ConnectionMethod recommendedMethod,
        @Nullable ConnectionSummary connection,
        List<MethodGuide> methods,
        List<Note> security,
        List<Faq> troubleshooting,
        List<String> supportedBanks,
        String supportedBanksNote) {

    public enum State {
        NOT_CONNECTED,
        CHOOSE_ACCOUNTS,
        SYNCING,
        CONNECTED,
        NEEDS_ATTENTION
    }

    public enum StepStatus {
        DONE,
        CURRENT,
        TODO,
        ATTENTION
    }

    public enum ActionType {
        OPEN_LINK,
        START_OAUTH,
        ENTER_TOKENS,
        UPDATE_TOKENS,
        CHOOSE_ACCOUNTS,
        SYNC_NOW
    }

    @Schema(name = "SetupGuideConnection")
    public record ConnectionSummary(
            UUID id,
            ConnectionMethod method,
            ConnectionStatus status,
            @Nullable Instant lastSyncedAt,
            int accountsAvailable,
            int accountsSyncing,
            @Nullable SyncStatus latestSyncStatus,
            @Nullable String latestSyncError) {}

    @Schema(name = "SetupGuideMethod")
    public record MethodGuide(
            ConnectionMethod method,
            boolean available,
            @Nullable String unavailableReason,
            String title,
            String summary,
            int estimatedMinutes,
            List<String> requirements,
            List<Step> steps) {}

    @Schema(name = "SetupGuideStep")
    public record Step(
            String id,
            String title,
            String body,
            @Nullable String tip,
            StepStatus status,
            List<Link> links,
            @Nullable Action action) {}

    @Schema(name = "SetupGuideLink")
    public record Link(String label, String url) {}

    @Schema(name = "SetupGuideAction")
    public record Action(
            ActionType type,
            String label,
            @Nullable UUID connectionId,
            @Nullable String url) {}

    @Schema(name = "SetupGuideNote")
    public record Note(String title, String body) {}

    @Schema(name = "SetupGuideFaq")
    public record Faq(String question, String answer) {}
}
