package nz.kiwifinance.income;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface IncomeSourceRepository extends JpaRepository<IncomeSource, UUID> {

    List<IncomeSource> findByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<IncomeSource> findByIdAndUserId(UUID id, UUID userId);
}
