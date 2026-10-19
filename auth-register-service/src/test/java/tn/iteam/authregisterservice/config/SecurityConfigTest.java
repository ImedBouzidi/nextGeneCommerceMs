package tn.iteam.authregisterservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import tn.iteam.authregisterservice.model.User;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigTest {

    @Test
    void mapsKeycloakRealmRolesToSpringRoles() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("customer@example.com")
                .claim("realm_access", Map.of("roles", List.of("CLIENT", "ADMIN")))
                .build();

        JwtAuthenticationConverter converter = new SecurityConfig().jwtAuthenticationConverter();

        var authorities = converter.convert(jwt).getAuthorities();
        assertTrue(authorities.contains(new SimpleGrantedAuthority("ROLE_CLIENT")));
        assertTrue(authorities.contains(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @Test
    void userEntityIsNotSpringUserDetails() {
        assertFalse(org.springframework.security.core.userdetails.UserDetails.class
                .isAssignableFrom(User.class));
    }
}
