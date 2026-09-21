package nz.kiwifinance.bankfeed;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface OAuthStateRepository extends JpaRepository<OAuthState, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OAuthState> findByStateHash(String stateHash);
}
