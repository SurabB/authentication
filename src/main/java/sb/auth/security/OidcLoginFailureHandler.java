package sb.auth.security;


import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

    @Component
    @RequiredArgsConstructor
    @Slf4j
    public class OidcLoginFailureHandler implements AuthenticationFailureHandler {

        @Value("${frontend-url}")
        private String frontendUrl;

        @Override
        public void onAuthenticationFailure(HttpServletRequest request,
                                            HttpServletResponse response,
                                            AuthenticationException exception) throws IOException, IOException {

            log.warn("OAuth2 login failed: {}", exception.getMessage(), exception);

            String errorMessage = resolveErrorMessage(exception);

            String redirectUrl = UriComponentsBuilder.fromUriString(frontendUrl + "/login")
                    .queryParam("error", URLEncoder.encode(errorMessage, StandardCharsets.UTF_8))
                    .build().toUriString();

            response.sendRedirect(redirectUrl);
        }

        private String resolveErrorMessage(AuthenticationException exception) {
            if (exception instanceof OAuth2AuthenticationException oauth2Ex) {
                OAuth2Error error = oauth2Ex.getError();
                return switch (error.getErrorCode()) {
                    case "access_denied" -> "You cancelled the Google sign-in.";
                    case "invalid_token", "invalid_id_token" -> "Google authentication failed. Please try again.";
                    default -> "Google sign-in failed. Please try again.";
                };
            }
            return "Authentication failed. Please try again.";
        }
    }

