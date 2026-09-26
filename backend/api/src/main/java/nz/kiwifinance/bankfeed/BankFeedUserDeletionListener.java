package nz.kiwifinance.bankfeed;

import java.util.List;
import lombok.RequiredArgsConstructor;
import nz.kiwifinance.auth.UserDeletionRequested;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Revokes provider access when someone deletes their Kiwi Finance account. The revocation runs
 * after the deletion commits, so the provider is never called inside a database transaction.
 */
@Component
@RequiredArgsConstructor
class BankFeedUserDeletionListener {

    private final BankConnectionRepository connections;
    private final BankConnectionService connectionService;

    @EventListener
    void onUserDeletion(UserDeletionRequested event) {
        List<BankConnection> current = connections.findCurrent(event.userId());
        if (current.isEmpty()) {
            return;
        }
        // The loaded connections keep their encrypted credentials in memory, which is what the
        // revocation needs once the rows themselves have been deleted.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                current.forEach(connectionService::revokeQuietly);
            }
        });
    }
}
