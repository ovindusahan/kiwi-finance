package nz.kiwifinance.transaction;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import nz.kiwifinance.common.web.Cursors;
import org.springframework.data.jpa.domain.Specification;

final class TransactionSpecifications {

    private TransactionSpecifications() {}

    static Specification<Transaction> matching(UUID userId, TransactionFilter filter, Cursors.DateCursor cursor) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));
            predicates.add(cb.isNull(root.get("deletedAt")));
            if (filter.accountId() != null) {
                predicates.add(cb.equal(root.get("accountId"), filter.accountId()));
            }
            if (filter.uncategorised()) {
                predicates.add(cb.isNull(root.get("categoryId")));
            } else if (filter.categoryId() != null) {
                predicates.add(cb.equal(root.get("categoryId"), filter.categoryId()));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("postedOn"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("postedOn"), filter.to()));
            }
            if (filter.direction() == TransactionFilter.Direction.IN) {
                predicates.add(cb.greaterThan(root.get("amountCents"), 0L));
            } else if (filter.direction() == TransactionFilter.Direction.OUT) {
                predicates.add(cb.lessThan(root.get("amountCents"), 0L));
            }
            if (filter.search() != null && !filter.search().isBlank()) {
                String pattern = "%" + escape(filter.search().trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("description")), pattern, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.get("merchant"), "")), pattern, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.get("notes"), "")), pattern, '\\')));
            }
            if (cursor != null) {
                predicates.add(cb.or(
                        cb.lessThan(root.get("postedOn"), cursor.date()),
                        cb.and(
                                cb.equal(root.get("postedOn"), cursor.date()),
                                cb.lessThan(root.get("id"), cursor.id()))));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
