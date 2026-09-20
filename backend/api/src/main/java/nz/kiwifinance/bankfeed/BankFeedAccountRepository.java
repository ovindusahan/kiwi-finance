package nz.kiwifinance.bankfeed;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface BankFeedAccountRepository extends JpaRepository<BankFeedAccount, UUID> {

    List<BankFeedAccount> findByConnectionIdOrderByNameAsc(UUID connectionId);

    Optional<BankFeedAccount> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndAccountIdAndSyncEnabledTrue(UUID userId, UUID accountId);
}
