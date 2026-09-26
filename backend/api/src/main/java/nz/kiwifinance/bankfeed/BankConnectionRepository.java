package nz.kiwifinance.bankfeed;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface BankConnectionRepository extends JpaRepository<BankConnection, UUID> {

    Optional<BankConnection> findByIdAndUserId(UUID id, UUID userId);

    @Query("""
            select c from BankConnection c
            where c.userId = :userId and c.status <> nz.kiwifinance.bankfeed.ConnectionStatus.DISCONNECTED
            order by c.createdAt
            """)
    List<BankConnection> findCurrent(UUID userId);

    @Query("""
            select c from BankConnection c
            where c.userId = :userId and c.provider = :provider
              and c.status <> nz.kiwifinance.bankfeed.ConnectionStatus.DISCONNECTED
            """)
    Optional<BankConnection> findCurrent(UUID userId, BankFeedProvider provider);

    Optional<BankConnection> findFirstByUserIdAndProviderAndExternalUserIdAndStatus(
            UUID userId, BankFeedProvider provider, String externalUserId, ConnectionStatus status);

    List<BankConnection> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @Query("select c.id from BankConnection c where c.status = nz.kiwifinance.bankfeed.ConnectionStatus.ACTIVE")
    List<UUID> findActiveIds();

    /**
     * Claims the connection for one sync at a time. Returns 1 if the lease was acquired, or 0 if
     * another sync holds it or the connection is no longer active.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update BankConnection c set c.syncLockedUntil = :until
            where c.id = :id and c.status = nz.kiwifinance.bankfeed.ConnectionStatus.ACTIVE
              and (c.syncLockedUntil is null or c.syncLockedUntil < :now)
            """)
    int acquireSyncLease(UUID id, Instant now, Instant until);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update BankConnection c set c.syncLockedUntil = null where c.id = :id")
    int releaseSyncLease(UUID id);
}
