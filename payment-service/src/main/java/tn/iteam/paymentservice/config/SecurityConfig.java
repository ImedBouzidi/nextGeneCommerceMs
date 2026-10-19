package tn.iteam.paymentservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.*;

@Configuration
@EnableMethodSecurity          // required for @PreAuthorize on the controller
public class SecurityConfig {

    /**
     * Note the path: the controller is mapped to {@code /api/payments}. The previous
     * rule matched {@code /payments/**}, which nothing ever hit, so it silently fell
     * through to {@code anyRequest().authenticated()} and the scope was never
     * actually enforced.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        // Stripe authenticates with a signature header, not a bearer
                        // token. Letting the JWT filter reject these first would return
                        // 401 to every webhook and silently mark nothing as paid, so
                        // the path must reach the controller — where the signature is
                        // actually verified and rejected if invalid.
                        .requestMatchers("/api/payments/webhooks/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/payments/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/payments/**").authenticated()
                        .requestMatchers("/api/admin/payments/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
                        .decoder(jwtDecoder())));

        return http.build();
    }

    // ======== CUSTOM KEYCLOAK ROLE MAPPING ========= //
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {

        JwtGrantedAuthoritiesConverter defaultConverter = new JwtGrantedAuthoritiesConverter();
        defaultConverter.setAuthorityPrefix("ROLE_");
        defaultConverter.setAuthoritiesClaimName("roles");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {

            Set<GrantedAuthority> authorities = new HashSet<>();

            // 1️⃣ ADD DEFAULT "roles" CLAIM (SAFE CONVERSION)
            Collection<GrantedAuthority> defaultAuthorities = defaultConverter.convert(jwt);
            if (defaultAuthorities != null) {
                authorities.addAll(defaultAuthorities);
            }

            // 2️⃣ REALM ROLES
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess != null) {
                List<String> realmRoles = (List<String>) realmAccess.get("roles");
                if (realmRoles != null) {
                    realmRoles.forEach(role ->
                            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                    );
                }
            }

            // 3️⃣ CLIENT ROLES (resource_access)
            Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
            if (resourceAccess != null) {
                resourceAccess.forEach((client, value) -> {
                    Map<String, Object> clientData = (Map<String, Object>) value;
                    List<String> clientRoles = (List<String>) clientData.get("roles");

                    if (clientRoles != null) {
                        clientRoles.forEach(role ->
                                authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
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