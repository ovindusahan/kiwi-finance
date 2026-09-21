package nz.kiwifinance.bankfeed;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

interface BankSyncRunRepository extends JpaRepository<BankSyncRun, UUID> {

    List<BankSyncRun> findByConnectionIdOrderByStartedAtDesc(UUID connectionId, Limit limit);

    Optional<BankSyncRun> findFirstByConnectionIdOrderByStartedAtDesc(UUID connectionId);

    Optional<BankSyncRun> findByIdAndUserId(UUID id, UUID userId);
}
