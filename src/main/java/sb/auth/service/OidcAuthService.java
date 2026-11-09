package sb.auth.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import sb.auth.entity.User;
import sb.auth.entity.UserIdentity;
import sb.auth.enums.Role;
import sb.auth.exceptionclass.VerificationFailedException;
import sb.auth.repo.UserIdentityRepo;
import sb.auth.repo.UserRepo;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OidcAuthService {

    private final UserRepo userRepo;
    private final UserIdentityRepo userIdentityRepo;

    @Transactional
    public User resolveOrCreateUser(OidcUser oidcUser, String provider) {
        if (!Boolean.TRUE.equals(oidcUser.getEmailVerified())) {
            throw new VerificationFailedException("Google account email is not verified");
        }

        Optional<UserIdentity> existing =
                userIdentityRepo.findByProviderAndSubj(provider, oidcUser.getSubject());

        if (existing.isPresent()) {
            UserIdentity userIdentity = existing.get();
            userIdentity.setName(oidcUser.getName());
            userIdentity.setEmail(oidcUser.getEmail());
            userIdentity.setProfilePictureUrl(oidcUser.getPicture());
            // managed entity inside this @Transactional method — dirty checking
            // flushes these changes automatically, no explicit save needed.
            return userIdentity.getUser();
        }

        User user = User.builder()
                .email(oidcUser.getEmail())
                .uuid(UUID.randomUUID().toString())
                .username(oidcUser.getFullName())
                .role(Role.USER)
                .isAccountVerified(Boolean.TRUE.equals(oidcUser.getEmailVerified()))
                .build();

        UserIdentity userIdentity = new UserIdentity();
        userIdentity.setProvider(provider);
        userIdentity.setProviderSubj(oidcUser.getSubject());
        userIdentity.setProfilePictureUrl(oidcUser.getPicture());
        userIdentity.setEmail(oidcUser.getEmail());
        userIdentity.setName(oidcUser.getFullName());
        user.addUserIdentity(userIdentity);

        return userRepo.save(user);
    }
}
