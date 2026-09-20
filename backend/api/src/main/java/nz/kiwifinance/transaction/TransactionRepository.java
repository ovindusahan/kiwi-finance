package nz.kiwifinance.transaction;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    @Query("select t from Transaction t where t.id = :id and t.userId = :userId and t.deletedAt is null")
    Optional<Transaction> findActive(UUID id, UUID userId);

    List<Transaction> findByAccountIdAndExternalIdIn(UUID accountId, Collection<String> externalIds);

    List<Transaction> findByAccountIdAndAwaitingBankCopyTrueAndDeletedAtIsNull(UUID accountId);

    @Query("""
            select t from Transaction t
            where t.userId = :userId and t.deletedAt is null and t.amountCents < 0 and t.transfer = false
              and (t.categorySource is null or t.categorySource <> nz.kiwifinance.transaction.CategorySource.USER)
              and t.postedOn >= :from
            order by t.postedOn desc""")
    List<Transaction> findUnsortedOutgoings(UUID userId, LocalDate from);

    @Query("""
            select coalesce(sum(t.amountCents), 0) from Transaction t
            where t.userId = :userId and t.accountId = :accountId and t.deletedAt is null
              and t.postedOn between :from and :to""")
    long sumForAccount(UUID userId, UUID accountId, LocalDate from, LocalDate to);

    @Query("""
            select t from Transaction t
            where t.userId = :userId and t.deletedAt is null and t.postedOn between :from and :to
            order by t.postedOn, t.id
            """)
    List<Transaction> findForAnalysis(UUID userId, LocalDate from, LocalDate to);

    @Query("""
            select t from Transaction t
            where t.userId = :userId and t.deletedAt is null
              and (t.categorySource is null or t.categorySource <> nz.kiwifinance.transaction.CategorySource.USER)
            """)
    List<Transaction> findAutomaticallyCategorised(UUID userId);

    @Modifying
    @Query(
            "update Transaction t set t.categoryId = null, t.categorySource = null where t.userId = :userId and t.categoryId = :categoryId")
    int clearCategory(UUID userId, UUID categoryId);

    @Query("select min(t.postedOn) from Transaction t where t.userId = :userId and t.deletedAt is null")
    Optional<LocalDate> findFirstPostedOn(UUID userId);

    long countByUserIdAndDeletedAtIsNull(UUID userId);
}
