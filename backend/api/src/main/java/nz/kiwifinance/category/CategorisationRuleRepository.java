package nz.kiwifinance.category;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CategorisationRuleRepository extends JpaRepository<CategorisationRule, UUID> {

    List<CategorisationRule> findByUserIdOrderByPriorityDescCreatedAtAsc(UUID userId);

    Optional<CategorisationRule> findByIdAndUserId(UUID id, UUID userId);
}
