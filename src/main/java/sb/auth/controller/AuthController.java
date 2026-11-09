package sb.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.*;
import sb.auth.config.AppProperties;
import sb.auth.dto.AuthResponse;
import sb.auth.dto.EmailPasswordDto;
import sb.auth.dto.PasswordResetRequest;
import sb.auth.exceptionclass.BlockedAccessException;
import sb.auth.exceptionclass.VerificationFailedException;
import sb.auth.repo.RefreshTokenRepo;
import sb.auth.repo.UserRepo;
import sb.auth.service.AuthService;
import sb.auth.service.PasswordResetService;
import sb.auth.service.TokenGeneration;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    @Value("${app.cookies.secure:false}")
    private boolean secureCookies;

    private final AuthService authService;
    private final CsrfTokenRepository csrfTokenRepository;
    private final AppProperties appProperties;
    private final PasswordResetService passwordResetService;
    private final UserRepo userRepo;
    private final RefreshTokenRepo refreshTokenRepo;

    @PostMapping("/login")
    public ResponseEntity<Map<String,String>> authenticate(@RequestBody EmailPasswordDto request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        AuthResponse authResponse = authService.login(request);
      return setCookiesAndRotateCsrf(httpRequest,httpResponse,authResponse);
    }

    @PostMapping("/refreshToken")
    public ResponseEntity<Map<String,String>> refreshToken(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {

        AuthResponse authResponse=authService.refreshToken(httpRequest);
        return setCookiesAndRotateCsrf(httpRequest, httpResponse, authResponse);


    }
    @GetMapping("/getCsrfToken")
    public ResponseEntity<Map<String,String>> getCsrfToken(CsrfToken csrfToken) {
       return  ResponseEntity.ok(Map.of("csrfToken",csrfToken.getToken()));
    }

    @NonNull
    private ResponseEntity<Map<String,String>> setCookiesAndRotateCsrf(HttpServletRequest httpRequest, HttpServletResponse httpResponse, AuthResponse authResponse) {
        long maxAgeDays = 60L * 60 * 24 * (appProperties.auth().refreshToken().time().inDays());
        ResponseCookie jwtCookie = ResponseCookie.from("jwt-t", authResponse.jwtToken())
                .httpOnly(true)
                .path("/")
                .maxAge((appProperties.auth().accessToken().time().inMinutes()+1)* 60L)
                .sameSite("Lax")
                .secure(secureCookies)
                .build();
        ResponseCookie refreshTokenCookie = ResponseCookie.from("refresh-t", authResponse.refreshToken())
                .httpOnly(true)
                .path("/")
                .maxAge(maxAgeDays+30)
                .sameSite("Lax")
                .secure(secureCookies)
                .build();

        csrfTokenRepository.saveToken(null,httpRequest,httpResponse);
        CsrfToken csrfToken = csrfTokenRepository.generateToken(httpRequest);

        csrfToken.getToken();
        csrfTokenRepository.saveToken(csrfToken,httpRequest,httpResponse);

        httpResponse.addHeader(HttpHeaders.SET_COOKIE, jwtCookie.toString());
        httpResponse.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString());
        AuthResponse.UserDetails userDetails = authResponse.userDetails();
        Map<String, String> responseBody = Map.of(

                "username", userDetails.username(),
                "role", userDetails.role(),
                "email", userDetails.email()

        );
        return ResponseEntity.ok(responseBody);
    }

    @PostMapping("/forgetPasswordSendCode")
    public ResponseEntity<String> handleForgetPassword(@RequestBody String email){
        String msg = passwordResetService.sendVerificationCode(email);
        return ResponseEntity.ok(msg);

    }
    @PostMapping("/verifyAndChangePassword")
    public @NonNull ResponseEntity<Map<String,String>> handleForgetPassword(@RequestBody PasswordResetRequest passwordResetRequest,
                                                                             HttpServletRequest httpServletRequest,
                                                                             HttpServletResponse httpServletResponse

    ){
        try {
            AuthResponse authResponse = passwordResetService.verifyRegistrationCode(passwordResetRequest);
            return setCookiesAndRotateCsrf(httpServletRequest,httpServletResponse,authResponse);
        } catch (VerificationFailedException | BlockedAccessException e) {
           return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error",e.getMessage()));
        }

    }


    public ResponseEntity<Void> invalidateCookieAndCsrf(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        ResponseCookie jwtCookie = ResponseCookie.from("jwt-t", "")
                .httpOnly(true)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .secure(secureCookies)
                .build();
        ResponseCookie refreshTokenCookie = ResponseCookie.from("refresh-t","")
                .httpOnly(true)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .secure(secureCookies)
                .build();
        csrfTokenRepository.saveToken(null,httpServletRequest,httpServletResponse);
        httpServletResponse.addHeader(HttpHeaders.SET_COOKIE, jwtCookie.toString());
        httpServletResponse.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString());

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse,@CookieValue(name = "refresh-t", required = false) String refreshToken) {
        if(refreshToken != null){
            String hashedToken = TokenGeneration.sha256(refreshToken);
            refreshTokenRepo.revoke(hashedToken);
        }
        return invalidateCookieAndCsrf(httpServletRequest, httpServletResponse);


    }

}
