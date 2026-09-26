package nz.kiwifinance.goal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface GoalRepository extends JpaRepository<Goal, UUID> {

    List<Goal> findByUserIdOrderByPriorityAscCreatedAtAsc(UUID userId);

    Optional<Goal> findByIdAndUserId(UUID id, UUID userId);
}
