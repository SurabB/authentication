package sb.auth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PasswordCredentials {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

   @Column(nullable = false)
   String password;

   @OneToOne
   @JoinColumn(name = "userId")
   User user;
}
