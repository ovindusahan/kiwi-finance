package nz.kiwifinance.budget;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface BudgetRepository extends JpaRepository<Budget, UUID> {

    Optional<Budget> findByUserIdAndActiveTrue(UUID userId);

    Optional<Budget> findByIdAndUserId(UUID id, UUID userId);
}
