package nz.kiwifinance.goal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface GoalContributionRepository extends JpaRepository<GoalContribution, UUID> {

    List<GoalContribution> findByGoalIdOrderByContributedOnDescCreatedAtDesc(UUID goalId);

    @Query("select coalesce(sum(c.amountCents), 0) from GoalContribution c where c.goalId = :goalId")
    long totalFor(UUID goalId);

    List<GoalContribution> findByUserId(UUID userId);
}
