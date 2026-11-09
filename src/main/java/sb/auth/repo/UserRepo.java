package sb.auth.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import sb.auth.entity.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepo extends JpaRepository<User, UUID> {

    @Query("select u from User u where u.email=:email")
    Optional<User> loadUserByUserEmail(String email);

    @Query("select u from User u join fetch RefreshToken r where u.username=:username")
    Optional<User> loadUserAndRefreshToken(String username);

    @Query("select case when count(u)>0 then true else false end from User u where u.email=:email")
    boolean checkUserExistence(String email);
}
