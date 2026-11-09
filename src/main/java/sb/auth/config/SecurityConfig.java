package sb.auth.config;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import sb.auth.entity.User;
import sb.auth.repo.UserRepo;
import sb.auth.security.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Optional;

@Slf4j
@Configuration(proxyBeanMethods = false)
@ConfigurationPropertiesScan
@EnableAsync
@EnableWebSecurity

public class SecurityConfig {
    @Bean
    public SecurityFilterChain httpSecurity(HttpSecurity httpSecurity,
                                            CsrfInvokerFilter csrfInvokerFilter,
                                            CustomAccessDeniedHandler accessDeniedHandler,
                                            CsrfTokenRepository csrfTokenRepository,
                                            NullAuthenticatedSessionStrategy nullAuthenticatedSessionStrategy,
                                            OidcLoginSuccessHandler oidcLoginSuccessHandler,
                                            OidcLoginFailureHandler oidcLoginFailureHandler,
                                            JwtAuthenticationConverter jwtAuthenticationConverter

    ){
        return httpSecurity
                .csrf(csrf->{
                    csrf.csrfTokenRepository(csrfTokenRepository);
                    csrf.sessionAuthenticationStrategy(nullAuthenticatedSessionStrategy);
                    var requestHandler=new CsrfTokenRequestAttributeHandler();
                    csrf.csrfTokenRequestHandler(requestHandler);
                })
                .exceptionHandling(e->{
                    e.accessDeniedHandler(accessDeniedHandler);
                })
                .authorizeHttpRequests(request-> request
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers("/oauth2/**").permitAll()
                        .requestMatchers("/login/**").permitAll()
                        .requestMatchers("/user/sendRegCode","/user/verifyAndRegisterUser").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                )
                .addFilterBefore(new CookieToBearerHeaderFilter("jwt-t"), BearerTokenAuthenticationFilter.class)
                .oauth2Login(oauth -> oauth
                        .successHandler(oidcLoginSuccessHandler)
                        .failureHandler(oidcLoginFailureHandler)
                )
                .sessionManagement(session->{
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS);
                    session.sessionAuthenticationStrategy(nullAuthenticatedSessionStrategy);
                })
                .addFilterAfter(csrfInvokerFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public UserDetailsService userDetailsService(UserRepo userRepo){
        return userEmail->{
            Optional<User> user = userRepo.loadUserByUserEmail(userEmail);
            if(user.isEmpty()){
                throw new UsernameNotFoundException("User not found");
            }
            return user.get();
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        authoritiesConverter.setAuthoritiesClaimName("roles");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }

    @Bean
    public JwtEncoder jwtEncoder(RSAPublicKey publicKey, RSAPrivateKey privateKey) {
        JWK jwk = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID("app-key-1")
                .build();
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(jwk));
        return new NimbusJwtEncoder(jwkSource);
    }


    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)throws Exception{

       return configuration.getAuthenticationManager();
    }
    @Bean
    public RSAPrivateKey privateKey(@Value("${app.jwt.private-key-location:classpath:private.pem}") Resource resource) throws Exception {
        String key;
        try (InputStream is = resource.getInputStream()) {
            key = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        String privateKeyPEM = key
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] decoded = Base64.getDecoder().decode(privateKeyPEM);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return (RSAPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }
    @Bean
    public RSAPublicKey publicKey(@Value("${app.jwt.public-key-location:classpath:public.pem}") Resource resource) throws Exception {
        String key;
        try (InputStream is = resource.getInputStream()) {
            key = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        String publicKeyPEM = key
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        byte[] decoded = Base64.getDecoder().decode(publicKeyPEM);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return (RSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(decoded));
    }
    @Bean
    public JwtDecoder jwtDecoder(RSAPublicKey publicKey) {
        return NimbusJwtDecoder.withPublicKey(publicKey).build();
    }
    @Bean
    public CsrfTokenRepository csrfTokenRepository(@Value("${app.cookies.secure:false}") boolean secureCookies) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/");
        repository.setCookieCustomizer(cookie -> cookie.secure(secureCookies));
        return repository;
    }

    @Bean
    public NullAuthenticatedSessionStrategy nullAuthenticatedSessionStrategy(){
        return new NullAuthenticatedSessionStrategy();
    }



}
