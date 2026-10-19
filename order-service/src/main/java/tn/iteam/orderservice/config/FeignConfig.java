package tn.iteam.orderservice.config;

import feign.RequestInterceptor;
import feign.Request;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@Configuration
@Slf4j
public class FeignConfig {

    /**
     * Intercepts Feign requests and adds the Authorization header
     * with the JWT token extracted from Spring Security context.
     */
    @Bean
    public RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken jwtAuth) {
                Jwt jwt = jwtAuth.getToken();
                String tokenValue = jwt.getTokenValue();
                if (tokenValue != null && !tokenValue.isEmpty()) {
                    String bearerToken = tokenValue.startsWith("Bearer ") ? tokenValue : "Bearer " + tokenValue;
                    requestTemplate.header("Authorization", bearerToken);
                    log.debug("Added Authorization header to Feign request: {}", bearerToken.substring(0, Math.min(20, bearerToken.length())) + "...");
                } else {
                    log.warn("JWT token value is null or empty");
                }
            } else {
                log.warn("Authentication is not a JwtAuthenticationToken. Type: {}, Auth: {}", 
                    auth != null ? auth.getClass().getName() : "null", auth);
            }
        };
    }

    // Optional: increase Feign timeouts
    @Bean
    public Request.Options options() {
        return new Request.Options(30000, 30000); // 30s connect and read timeout
    }
}