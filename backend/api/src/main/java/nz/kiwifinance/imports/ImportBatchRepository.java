package nz.kiwifinance.imports;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

interface ImportBatchRepository extends JpaRepository<ImportBatch, UUID> {

    List<ImportBatch> findByUserIdOrderByCreatedAtDesc(UUID userId, Limit limit);
}
