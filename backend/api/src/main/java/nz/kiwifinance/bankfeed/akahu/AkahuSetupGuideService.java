package nz.kiwifinance.bankfeed.akahu;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import nz.kiwifinance.bankfeed.BankConnection;
import nz.kiwifinance.bankfeed.BankConnectionService;
import nz.kiwifinance.bankfeed.BankFeedAccount;
import nz.kiwifinance.bankfeed.BankFeedProvider;
import nz.kiwifinance.bankfeed.BankFeedResponses;
import nz.kiwifinance.bankfeed.BankSyncRun;
import nz.kiwifinance.bankfeed.ConnectionMethod;
import nz.kiwifinance.bankfeed.ConnectionStatus;
import nz.kiwifinance.bankfeed.SyncStatus;
import nz.kiwifinance.bankfeed.akahu.SetupGuide.Action;
import nz.kiwifinance.bankfeed.akahu.SetupGuide.ActionType;
import nz.kiwifinance.bankfeed.akahu.SetupGuide.State;
import nz.kiwifinance.bankfeed.akahu.SetupGuide.StepStatus;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Builds the Akahu setup guide for one person from the guide's content and their actual progress:
 * whether they have a connection, which accounts Akahu shares, which they sync, and how the last
 * sync went.
 */
@Service
class AkahuSetupGuideService {

    private static final String CONTENT = "bankfeed/akahu-setup-guide.json";

    private final SetupGuideContent content;
    private final BankConnectionService connections;
    private final AkahuProperties properties;

    AkahuSetupGuideService(JsonMapper jsonMapper, BankConnectionService connections, AkahuProperties properties)
            throws IOException {
        try (InputStream input = new ClassPathResource(CONTENT).getInputStream()) {
            this.content = jsonMapper.readValue(input, SetupGuideContent.class);
        }
        this.connections = connections;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    SetupGuide guideFor(UUID userId) {
        Optional<BankConnection> connection = connections.current(userId, BankFeedProvider.AKAHU);
        Progress progress = connection.map(this::progressOf).orElse(Progress.NONE);
        boolean oauthAvailable = properties.oauthConfigured();

        List<SetupGuide.MethodGuide> methods = new ArrayList<>();
        methods.add(method(ConnectionMethod.OAUTH, oauthAvailable, progress));
        methods.add(method(ConnectionMethod.PERSONAL_APP, true, progress));

        ConnectionMethod recommended = connection
                .map(BankConnection::getMethod)
                .orElse(oauthAvailable ? ConnectionMethod.OAUTH : ConnectionMethod.PERSONAL_APP);
        methods.sort((a, b) -> Boolean.compare(b.method() == recommended, a.method() == recommended));

        State state = state(progress);
        return new SetupGuide(
                state,
                headline(state),
                recommended,
                connection.map(c -> summary(c, progress)).orElse(null),
                methods,
                content.security(),
                content.troubleshooting(),
                content.supportedBanks(),
                content.supportedBanksNote());
    }

    private Progress progressOf(BankConnection connection) {
        List<BankFeedAccount> accounts = connections.feedAccounts(connection.getId());
        BankSyncRun latest = connections.latestSync(connection.getId()).orElse(null);
        return new Progress(
                connection,
                accounts.size(),
                (int) accounts.stream().filter(BankFeedAccount::isSyncEnabled).count(),
                latest);
    }

    private SetupGuide.MethodGuide method(ConnectionMethod method, boolean available, Progress progress) {
        SetupGuideContent.Method text = content.methods().get(method);
        boolean thisMethod =
                progress.connection() != null && progress.connection().getMethod() == method;
        List<SetupGuide.Step> steps = new ArrayList<>();
        boolean currentAssigned = false;
        for (SetupGuideContent.Step step : text.steps()) {
            StepStatus status = statusOf(step.id(), progress, thisMethod);
            if (status == StepStatus.TODO && !currentAssigned) {
                status = StepStatus.CURRENT;
            }
            if (status == StepStatus.CURRENT || status == StepStatus.ATTENTION) {
                currentAssigned = true;
            }
            String body = status == StepStatus.ATTENTION ? attentionBody(step.id(), step.body()) : step.body();
            steps.add(new SetupGuide.Step(
                    step.id(),
                    step.title(),
                    body,
                    step.tip(),
                    status,
                    step.links() == null ? List.of() : step.links(),
                    action(step.id(), status, progress)));
        }
        return new SetupGuide.MethodGuide(
                method,
                available,
                available ? null : "One-click connections aren't set up on this server yet.",
                text.title(),
                text.summary(),
                text.estimatedMinutes(),
                text.requirements(),
                steps);
    }

    /**
     * A step is done once there is evidence of it. Steps before connecting can't be observed
     * directly, so they count as done as soon as a connection exists.
     */
    private StepStatus statusOf(String stepId, Progress progress, boolean thisMethod) {
        BankConnection connection = progress.connection();
        boolean connected = connection != null;
        boolean needsReconnect = connected && connection.getStatus() == ConnectionStatus.REAUTH_REQUIRED;
        return switch (stepId) {
            case "create-akahu-account" -> connected ? StepStatus.DONE : StepStatus.TODO;
            case "connect-banks" ->
                !connected
                        ? StepStatus.TODO
                        : progress.accountsAvailable() > 0 ? StepStatus.DONE : StepStatus.ATTENTION;
            case "create-personal-app" -> connected && thisMethod ? StepStatus.DONE : StepStatus.TODO;
            case "enter-tokens", "connect-with-akahu" -> {
                if (!connected || !thisMethod) {
                    yield StepStatus.TODO;
                }
                yield needsReconnect ? StepStatus.ATTENTION : StepStatus.DONE;
            }
            case "choose-accounts" -> connected && progress.accountsSyncing() > 0 ? StepStatus.DONE : StepStatus.TODO;
            case "first-sync" -> {
                if (!connected || progress.accountsSyncing() == 0) {
                    yield StepStatus.TODO;
                }
                BankSyncRun latest = progress.latestSync();
                if (latest != null && latest.getStatus() == SyncStatus.FAILED && connection.getLastSyncedAt() == null) {
                    yield StepStatus.ATTENTION;
                }
                yield connection.getLastSyncedAt() != null ? StepStatus.DONE : StepStatus.TODO;
            }
            default -> StepStatus.TODO;
        };
    }

    private String attentionBody(String stepId, String fallback) {
        return switch (stepId) {
            case "enter-tokens", "connect-with-akahu" -> content.attention().get("tokens-rejected");
            case "connect-banks" -> content.attention().get("no-accounts");
            case "first-sync" -> content.attention().get("sync-failed");
            default -> fallback;
        };
    }

    private Action action(String stepId, StepStatus status, Progress progress) {
        if (status == StepStatus.DONE) {
            return null;
        }
        UUID connectionId =
                progress.connection() == null ? null : progress.connection().getId();
        return switch (stepId) {
            case "create-akahu-account", "connect-banks" ->
                new Action(ActionType.OPEN_LINK, "Open Akahu", null, properties.portalUrl());
            case "create-personal-app" ->
                new Action(
                        ActionType.OPEN_LINK, "Open the Developers page", null, properties.portalUrl() + "/developers");
            case "enter-tokens" ->
                connectionId == null
                        ? new Action(ActionType.ENTER_TOKENS, "Enter tokens", null, null)
                        : new Action(ActionType.UPDATE_TOKENS, "Enter new tokens", connectionId, null);
            case "connect-with-akahu" ->
                new Action(
                        ActionType.START_OAUTH,
                        connectionId == null ? "Connect with Akahu" : "Reconnect with Akahu",
                        connectionId,
                        null);
            case "choose-accounts" ->
                connectionId == null
                        ? null
                        : new Action(ActionType.CHOOSE_ACCOUNTS, "Choose accounts", connectionId, null);
            case "first-sync" ->
                connectionId == null || progress.accountsSyncing() == 0
                        ? null
                        : new Action(ActionType.SYNC_NOW, "Sync now", connectionId, null);
            default -> null;
        };
    }

    private static State state(Progress progress) {
        BankConnection connection = progress.connection();
        if (connection == null) {
            return State.NOT_CONNECTED;
        }
        BankSyncRun latest = progress.latestSync();
        if (connection.getStatus() == ConnectionStatus.REAUTH_REQUIRED
                || (latest != null && latest.getStatus() == SyncStatus.FAILED)) {
            return State.NEEDS_ATTENTION;
        }
        if (latest != null && latest.getStatus() == SyncStatus.RUNNING) {
            return State.SYNCING;
        }
        if (progress.accountsSyncing() == 0) {
            return State.CHOOSE_ACCOUNTS;
        }
        return connection.getLastSyncedAt() == null ? State.SYNCING : State.CONNECTED;
    }

    private static String headline(State state) {
        return switch (state) {
            case NOT_CONNECTED -> "Connect your bank to see where your money goes";
            case CHOOSE_ACCOUNTS -> "Akahu is connected. Choose the accounts to follow";
            case SYNCING -> "Bringing in your transactions";
            case CONNECTED -> "Your bank is connected";
            case NEEDS_ATTENTION -> "Your bank connection needs attention";
        };
    }

    private static SetupGuide.ConnectionSummary summary(BankConnection connection, Progress progress) {
        BankSyncRun latest = progress.latestSync();
        return new SetupGuide.ConnectionSummary(
                connection.getId(),
                connection.getMethod(),
                connection.getStatus(),
                connection.getLastSyncedAt(),
                progress.accountsAvailable(),
                progress.accountsSyncing(),
                latest == null ? null : latest.getStatus(),
                latest == null ? null : BankFeedResponses.SyncRun.from(latest).errorMessage());
    }

    private record Progress(
            BankConnection connection, int accountsAvailable, int accountsSyncing, BankSyncRun latestSync) {
        static final Progress NONE = new Progress(null, 0, 0, null);
    }
}
