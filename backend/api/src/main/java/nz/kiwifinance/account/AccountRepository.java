package nz.kiwifinance.account;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByIdAndUserId(UUID id, UUID userId);

    List<Account> findByUserIdOrderByCreatedAtAsc(UUID userId);

    List<Account> findByUserIdAndArchivedAtIsNullOrderByCreatedAtAsc(UUID userId);
}
