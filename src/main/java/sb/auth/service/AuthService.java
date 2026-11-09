package sb.auth.service;


import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import sb.auth.dto.AuthResponse;
import sb.auth.dto.EmailPasswordDto;
import sb.auth.entity.RefreshToken;
import sb.auth.entity.User;
import sb.auth.repo.RefreshTokenRepo;
import sb.auth.repo.UserRepo;
import sb.auth.security.JwtService;

import java.time.LocalDateTime;
import java.util.*;


@Slf4j
@AllArgsConstructor
@Service
@Transactional
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final UserRepo userRepo;
    private final RefreshTokenRepo refreshTokenRepo;

    public AuthResponse login(EmailPasswordDto request) {

        Authentication authenticate = authenticationManager.authenticate(
                 UsernamePasswordAuthenticationToken.unauthenticated(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        User userDetails = (User) authenticate.getPrincipal();

         if(userDetails==null){
             throw new BadCredentialsException("Username or password not valid");
         }
       RefreshToken refreshToken = refreshTokenService.generateRefreshToken(userDetails.getRefreshTokenObj(),true);
         String unhashedRefreshToken=refreshToken.getToken();
         refreshToken.setToken(TokenGeneration.sha256(unhashedRefreshToken));
         userDetails.setRefreshTokenObj(refreshToken);
         refreshToken.setUser(userDetails);
         userRepo.save(userDetails);
        List<String> roleNames = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        Map<String,Object> extraClaims=HashMap.newHashMap(3);
        extraClaims.put("roles",roleNames);
        extraClaims.put("username",userDetails.getUsername());
        extraClaims.put("email",userDetails.getEmail());



        String jwtToken = jwtService.generateAccessToken(userDetails.getUuid(),extraClaims);
        AuthResponse.UserDetails authUserDetail = new AuthResponse.UserDetails(userDetails.getUsername(), userDetails.getRole().name(),userDetails.getEmail());

        return new AuthResponse(jwtToken,unhashedRefreshToken,authUserDetail);
    }

    public AuthResponse refreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if(cookies==null || cookies.length==0){
            log.warn("No cookies found");
            throw new BadCredentialsException("Authentication Failed: Cookies is absent");
        }
        Optional<Cookie> optionalRefresh = Arrays.stream(cookies).filter(c -> c.getName().equals("refresh-t")).findFirst();
        if(optionalRefresh.isEmpty()){
            log.warn("No cookies found");
            throw new BadCredentialsException("Authentication Failed: Cookie is absent");
        }
        String refreshToken = optionalRefresh.get().getValue();

        Optional<RefreshToken> optionalRefreshTokenFromDb = refreshTokenRepo.findByRefreshToken(TokenGeneration.sha256(refreshToken));
        if(optionalRefreshTokenFromDb.isEmpty()||optionalRefreshTokenFromDb.get().getUser()==null){
            log.warn("No refresh security found in db");
           throw new BadCredentialsException("Authentication Failed: Invalid Credentials");
       }
        RefreshToken refreshTokenFromDb = optionalRefreshTokenFromDb.get();
        User user = refreshTokenFromDb.getUser();

        if(refreshTokenFromDb.isRevoked()){
            log.warn("Revoked refresh security");
            throw new BadCredentialsException("Authentication Failed: Invalid Refresh Token");


        }

        if(refreshTokenFromDb.getExpiresAt().isBefore(LocalDateTime.now())){
            log.warn("Refresh Token expired");
            throw new BadCredentialsException("Authentication Failed: Invalid Refresh Token");
        }
        RefreshToken newRefreshToken= refreshTokenService.generateRefreshToken(user.getRefreshTokenObj(), false);
        String unhashedRefreshToken=newRefreshToken.getToken();
        String hashedToken= TokenGeneration.sha256(unhashedRefreshToken);
        newRefreshToken.setToken(hashedToken);
        user.setRefreshTokenObj(newRefreshToken);
        User savedUser = userRepo.save(user);
        List<String> roleNames = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        Map<String, Object> extraClaims=HashMap.newHashMap(3);
        extraClaims.put("roles",roleNames);
        extraClaims.put("username",user.getUsername());
        extraClaims.put("email",user.getEmail());

        String newJwt = jwtService.generateAccessToken(savedUser.getUuid(),extraClaims);
        AuthResponse.UserDetails authUserDetail = new AuthResponse.UserDetails(user.getUsername(), user.getRole().name(),user.getEmail());

        return new AuthResponse(newJwt,unhashedRefreshToken,authUserDetail);


    }
}
