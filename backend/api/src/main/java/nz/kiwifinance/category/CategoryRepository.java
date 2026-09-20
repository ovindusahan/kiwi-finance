package nz.kiwifinance.category;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface CategoryRepository extends JpaRepository<Category, UUID> {

    @Query("select c from Category c where c.userId is null or c.userId = :userId order by c.group, c.name")
    List<Category> findVisibleTo(UUID userId);

    @Query("select c from Category c where c.id = :id and (c.userId is null or c.userId = :userId)")
    Optional<Category> findVisible(UUID id, UUID userId);

    Optional<Category> findBySlugAndUserIdIsNull(String slug);

    @Query("select count(c) > 0 from Category c where c.userId = :userId and lower(c.name) = lower(:name)")
    boolean existsCustomName(UUID userId, String name);
}
