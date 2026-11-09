package sb.auth.repo;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import sb.auth.entity.RefreshToken;

import java.util.Optional;
import java.util.UUID;

@Transactional
public interface RefreshTokenRepo extends JpaRepository<RefreshToken, UUID> {
 @Query("select r from RefreshToken r where r.token=:refreshToken")
    Optional<RefreshToken> findByRefreshToken(String refreshToken);

 @Modifying
  @Query("delete from RefreshToken r where r.token=:refreshToken")
    void revoke(String refreshToken);
}
