package sb.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import sb.auth.enums.Role;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table
@Builder
@ToString(exclude = {"refreshTokenObj", "userIdentities"})
public class User implements UserDetails
{
    @Getter
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;


    @Getter
    @Column(unique = true,nullable = false)
    String email;

    @Getter
@Column(nullable = false)
    String uuid;


    @Column(nullable = false)
    String username;


    @Getter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    Role role;

   @Getter
   @Column(nullable = false)
   boolean isAccountVerified;


    @Getter
    @OneToOne(cascade = CascadeType.ALL,orphanRemoval = true,fetch = FetchType.EAGER,
    mappedBy = "user"
    )
    RefreshToken refreshTokenObj;
    @Getter
    @OneToOne(cascade = CascadeType.ALL,orphanRemoval = true,fetch = FetchType.EAGER,
            mappedBy = "user"
    )
    PasswordCredentials passwordCredentials;

    @Builder.Default
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<UserIdentity> userIdentities = new ArrayList<>();

    public void addUserIdentity(UserIdentity identity) {
        userIdentities.add(identity);
        identity.setUser(this);
    }

    public void removeUserIdentity(UserIdentity identity) {
        userIdentities.remove(identity);
        identity.setUser(null);
    }





    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.name()));
    }

    @Override
    public boolean isAccountNonExpired() {
        return UserDetails.super.isAccountNonExpired();
    }

    @Override
    public boolean isAccountNonLocked() {
        return UserDetails.super.isAccountNonLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return UserDetails.super.isCredentialsNonExpired();
    }

    @Override
    public boolean isEnabled() {
        return isAccountVerified;
    }

    @Override
    public @Nullable String getPassword() {
        if(passwordCredentials == null){
            return null;
        }
        return passwordCredentials.password;
    }

    @Override
    public String getUsername() {
        return username;
    }

}
