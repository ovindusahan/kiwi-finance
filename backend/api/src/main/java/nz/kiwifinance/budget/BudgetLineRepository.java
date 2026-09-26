package nz.kiwifinance.budget;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface BudgetLineRepository extends JpaRepository<BudgetLine, UUID> {

    List<BudgetLine> findByBudgetId(UUID budgetId);

    @Modifying(flushAutomatically = true)
    @Query("delete from BudgetLine l where l.budgetId = :budgetId")
    void deleteByBudgetId(UUID budgetId);
}
