package sb.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;
import sb.auth.config.AppProperties;
import sb.auth.entity.RefreshToken;
import sb.auth.entity.User;
import sb.auth.repo.UserRepo;
import sb.auth.service.OidcAuthService;
import sb.auth.service.RefreshTokenService;
import sb.auth.service.TokenGeneration;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Slf4j
@Component
public class OidcLoginSuccessHandler implements AuthenticationSuccessHandler {
    @Value("${app.cookies.secure:false}")
    private boolean secureCookies;


    private final OidcAuthService oidcAuthService;
    private final JwtService jwtService;
    private final AppProperties appProperties;
    private final CsrfTokenRepository csrfTokenRepository;
    private final RefreshTokenService refreshTokenService;
    private final UserRepo userRepo;

    @Value("${frontend-url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(@NonNull HttpServletRequest request,
                                        @NonNull HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OidcUser oidcUser = (OidcUser) authentication.getPrincipal();
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        String provider = oauthToken.getAuthorizedClientRegistrationId();

        if (oidcUser == null) {
            response.sendRedirect(frontendUrl + "/login?error=" +
                    URLEncoder.encode("Error while authenticating", StandardCharsets.UTF_8));
            return;
        }

        if (!Boolean.TRUE.equals(oidcUser.getEmailVerified())) {
            response.sendRedirect(frontendUrl + "/login?error=" +
                    URLEncoder.encode("Google account email is not verified", StandardCharsets.UTF_8));
            return;
        }

        try {
            User user = oidcAuthService.resolveOrCreateUser(oidcUser, provider);
            issueTokensAndRedirect(user, request, response);
        } catch (DataIntegrityViolationException ex) {
            response.sendRedirect(frontendUrl + "/login?error=" +
                    URLEncoder.encode("An account with this email already exists. Please sign in with your password.", StandardCharsets.UTF_8));
        } catch (Exception ex) {
            log.error("OIDC login failed", ex);
            response.sendRedirect(frontendUrl + "/login?error=" +
                    URLEncoder.encode("Something went wrong. Please try again.", StandardCharsets.UTF_8));
        }
    }

    // addHttpOnlyCookie + issueTokensAndRedirect unchanged

        private void addHttpOnlyCookie(HttpServletResponse response, String name, String value, int maxAgeSeconds) {
            ResponseCookie cookie = ResponseCookie.from(name, value)
                    .httpOnly(true)
                    .path("/")
                    .maxAge(maxAgeSeconds)
                    .sameSite("Lax")
                    .secure(secureCookies)
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        }

    private void issueTokensAndRedirect(User user, HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, Object> extraClaims = new HashMap<>();
        List<String> roleNames = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        extraClaims.put("roles", roleNames);
        extraClaims.put("username", user.getUsername());
        extraClaims.put("email", user.getEmail());

        String accessToken = jwtService.generateAccessToken(user.getUuid(),extraClaims);
        RefreshToken refreshToken = refreshTokenService.generateRefreshToken(user.getRefreshTokenObj(), true);
        String unhashedToken = refreshToken.getToken();
        refreshToken.setToken(TokenGeneration.sha256(unhashedToken));
        user.setRefreshTokenObj(refreshToken);
        refreshToken.setUser(user);
        userRepo.save(user);

        int accessTime = appProperties.auth().accessToken().time().inMinutes();
        int refreshTime = appProperties.auth().refreshToken().time().inDays();
        addHttpOnlyCookie(response, "jwt-t", accessToken, accessTime * 60);
        addHttpOnlyCookie(response, "refresh-t", unhashedToken, refreshTime * 24 * 60 * 60);

        CsrfToken csrfToken = csrfTokenRepository.generateToken(request);
        csrfTokenRepository.saveToken(csrfToken, request, response);

        response.sendRedirect(frontendUrl + "/dashboard");
    }
    }





