package sb.auth.repo;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import sb.auth.entity.UserIdentity;

import java.util.Optional;
import java.util.UUID;

@Transactional
public interface UserIdentityRepo extends JpaRepository<UserIdentity, UUID> {
    @Query("select ui from UserIdentity ui join fetch ui.user u where ui.provider=:provider and ui.providerSubj=:sub")
    Optional<UserIdentity> findByProviderAndSubj(String provider,String sub);
}
