package nz.kiwifinance.bankfeed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import nz.kiwifinance.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class BankSyncLeaseIntegrationTest extends IntegrationTestBase {

    @Autowired
    private BankSyncService syncs;

    @MockitoBean
    private BankFeedClients clients;

    @MockitoSpyBean
    private BankConnectionRepository connections;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void aSyncThatLooksFinishedCanBeFollowedStraightAway() {
        UUID userId = register("Aroha").id();
        UUID connectionId = UUID.randomUUID();
        jdbc.update("""
                insert into bank_connections
                    (id, user_id, provider, method, status, credentials, credentials_key_id, external_user_id,
                     created_at, updated_at)
                values (?, ?, 'AKAHU', 'PERSONAL_APP', 'ACTIVE', '\\x00', 'test', 'akahu_user', now(), now())
                """, connectionId, userId);
        BankFeedClient client = mock(BankFeedClient.class);
        when(client.accounts(any())).thenReturn(List.of());
        when(clients.forProvider(BankFeedProvider.AKAHU)).thenReturn(client);
        // Widen the moment the lease is released, so any gap between recording the outcome and
        // releasing the lease shows up every time.
        doAnswer(invocation -> {
                    Thread.sleep(500);
                    entityManager.flush();
                    int released = jdbc.update(
                            "update bank_connections set sync_locked_until = null where id = ?", connectionId);
                    entityManager.clear();
                    return released;
                })
                .when(connections)
                .releaseSyncLease(connectionId);

        UUID first = syncs.requestSync(userId, connectionId, SyncTrigger.MANUAL).getId();
        await().atMost(Duration.ofSeconds(10))
                .until(() -> syncs.run(userId, first).getStatus() != SyncStatus.RUNNING);

        UUID second =
                syncs.requestSync(userId, connectionId, SyncTrigger.MANUAL).getId();
        assertThat(second).isNotEqualTo(first);
        await().atMost(Duration.ofSeconds(10))
                .until(() -> syncs.run(userId, second).getStatus() != SyncStatus.RUNNING);
    }
}
