package tn.iteam.authregisterservice.config;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KeycloakConfig {
    @Bean
    public Keycloak keycloakAdminClient(
            @Value("${keycloak.auth-server-url}") String serverUrl,
            @Value("${KEYCLOAK_ADMIN_USERNAME}") String username,
            @Value("${KEYCLOAK_ADMIN_PASSWORD}") String password) {
        return KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm("master")                      // Usually "master" for admin access
                .username(username)
                .password(password)
                .clientId("admin-cli")                // admin-cli client
                .build();
    }
}
