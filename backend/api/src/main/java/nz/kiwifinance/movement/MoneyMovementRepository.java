package nz.kiwifinance.movement;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface MoneyMovementRepository extends JpaRepository<MoneyMovement, UUID> {

    @Query("""
            select m from MoneyMovement m
            where m.userId = :userId
              and (:accountId is null or m.fromAccountId = :accountId or m.toAccountId = :accountId)
            order by m.movedOn desc, m.createdAt desc""")
    List<MoneyMovement> findRecent(UUID userId, UUID accountId, Limit limit);
}
