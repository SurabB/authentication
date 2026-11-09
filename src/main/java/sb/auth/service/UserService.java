package sb.auth.service;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import sb.auth.dto.AuthResponse;
import sb.auth.dto.EmailPasswordDto;
import sb.auth.dto.RegisterRequest;
import sb.auth.entity.PasswordCredentials;
import sb.auth.entity.RefreshToken;
import sb.auth.entity.User;
import sb.auth.enums.Role;
import sb.auth.exceptionclass.BlockedAccessException;
import sb.auth.exceptionclass.VerificationFailedException;
import sb.auth.repo.UserRepo;
import sb.auth.security.JwtService;

import java.util.*;

@Service
@Transactional
@AllArgsConstructor
public class UserService {
    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;



    public void createUser(RegisterRequest userToRegister, Role role) {
        User user = new User();
        user.setRole(role);
        user.setUuid(UUID.randomUUID().toString());
        user.setEmail(userToRegister.email());
        user.setUsername(userToRegister.username());
        PasswordCredentials passwordCredentials = new PasswordCredentials();
        passwordCredentials.setPassword(passwordEncoder.encode(userToRegister.password()));
        user.setAccountVerified(role == Role.USER);
        passwordCredentials.setUser(user);
        user.setPasswordCredentials(passwordCredentials);
        userRepo.save(user);
    }


    public AuthResponse resetPassword(EmailPasswordDto emailPasswordDto) {
        Optional<User> optionalUser = userRepo.loadUserByUserEmail(emailPasswordDto.getEmail());
        if(optionalUser.isEmpty()){
            throw new VerificationFailedException("User not found");

        }
        User user = optionalUser.get();
        if(!user.isAccountVerified()){
            throw new BlockedAccessException("User not verified");
        }
        RefreshToken newRefreshTokenObj = refreshTokenService.generateRefreshToken(user.getRefreshTokenObj(), true);
        String unhashedToken = newRefreshTokenObj.getToken();
        String hashedToken = TokenGeneration.sha256(unhashedToken);
        newRefreshTokenObj.setToken(hashedToken);
        PasswordCredentials passwordCredentials = new PasswordCredentials();
        passwordCredentials.setPassword(passwordEncoder.encode(emailPasswordDto.getPassword()));
        passwordCredentials.setUser(user);
        user.setPasswordCredentials(passwordCredentials);
        user.setRefreshTokenObj(newRefreshTokenObj);
       userRepo.save(user);
        List<String> roleNames = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        Map<String, Object> extraClaims= HashMap.newHashMap(2);
        extraClaims.put("roles",roleNames);
        extraClaims.put("username",user.getUsername());
        extraClaims.put("email",user.getEmail());



        String newJwt = jwtService.generateAccessToken(user.getUuid(),extraClaims);
        AuthResponse.UserDetails authUserDetail = new AuthResponse.UserDetails(user.getUsername(), user.getRole().name(),user.getEmail());

       return new AuthResponse(newJwt,unhashedToken,authUserDetail);
    }
}
