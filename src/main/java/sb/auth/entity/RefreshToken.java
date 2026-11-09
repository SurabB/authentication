package sb.auth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;


@Entity
@Table
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID refreshId;

    @Column(unique = true, nullable = false)
    String token;

    @Column(nullable = false)
    LocalDateTime expiresAt;

    @Column(nullable = false)
    LocalDateTime issuedAt;

    @Column(nullable = false)
    boolean revoked;

    @OneToOne
    @JoinColumn(name="userId")
    User user;

}
