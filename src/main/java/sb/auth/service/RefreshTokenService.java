package sb.auth.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import sb.auth.config.AppProperties;
import sb.auth.entity.RefreshToken;
import sb.auth.repo.UserRepo;

import java.time.LocalDateTime;

@RequiredArgsConstructor
@Service
public class RefreshTokenService {
    private final UserRepo userRepo;
    private final AppProperties appProperties;

@Transactional
    public RefreshToken generateRefreshToken(RefreshToken refreshTokenParam, boolean isNewToken){
        RefreshToken refreshToken = refreshTokenParam==null?new RefreshToken():refreshTokenParam;
        String token=TokenGeneration.generateToken();



      if(isNewToken){
          refreshToken.setToken(token);
          refreshToken.setIssuedAt(LocalDateTime.now());
          int time= appProperties.auth().refreshToken().time().inDays();
          refreshToken.setExpiresAt(LocalDateTime.now().plusDays(time));
          refreshToken.setRevoked(false);

      }
      else{
          refreshToken.setToken(token);
      }

      return refreshToken;


    }



}
