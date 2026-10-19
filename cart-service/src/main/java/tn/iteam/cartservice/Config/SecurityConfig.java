package tn.iteam.cartservice.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.*;
import java.util.function.Function;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers("/api/cart/**").authenticated()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                );

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Set<GrantedAuthority> authorities = new HashSet<>();

            // Add scope roles
            JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();
            Collection<GrantedAuthority> scopeAuthorities = scopeConverter.convert(jwt);
            if (scopeAuthorities != null) authorities.addAll(scopeAuthorities);

            // Add realm roles
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess != null && realmAccess.get("roles") instanceof Collection) {
                ((Collection<?>) realmAccess.get("roles")).forEach(
                        r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r))
                );
            }

            // Add client roles
            Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
            if (resourceAccess != null) {
                resourceAccess.forEach((client, val) -> {
                    if (val instanceof Map<?, ?> clientMap && clientMap.get("roles") instanceof Collection) {
                        ((Collection<?>) clientMap.get("roles")).forEach(
                                r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r))
                        );
                    }
                });
            }

            return authorities;
        });

        // Set principal to the 'sub' claim (Keycloak user ID)
        converter.setPrincipalClaimName("sub");

        return converter;
    }


    @Bean
    public JwtDecoder jwtDecoder() {
        // JWKS URI from Keycloak realm
        return NimbusJwtDecoder
                .withJwkSetUri("http://localhost:8080/realms/e-commerce/protocol/openid-connect/certs")
                .build();
    }
}
