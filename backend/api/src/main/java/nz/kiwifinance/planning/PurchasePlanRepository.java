package nz.kiwifinance.planning;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PurchasePlanRepository extends JpaRepository<PurchasePlan, UUID> {

    List<PurchasePlan> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<PurchasePlan> findByIdAndUserId(UUID id, UUID userId);
}
