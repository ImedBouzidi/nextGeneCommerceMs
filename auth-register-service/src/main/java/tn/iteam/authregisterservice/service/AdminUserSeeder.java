package tn.iteam.authregisterservice.service;

import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tn.iteam.authregisterservice.model.Admin;
import tn.iteam.authregisterservice.model.Role;
import tn.iteam.authregisterservice.model.User;
import tn.iteam.authregisterservice.repos.RoleRepository;
import tn.iteam.authregisterservice.repos.UserRepository;

import javax.ws.rs.core.Response;
import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(value = "app.admin.seed.enabled", havingValue = "true", matchIfMissing = true)
public class AdminUserSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final Keycloak keycloakAdminClient;
    private final String realm;
    private final String email;
    private final String password;

    public AdminUserSeeder(
            UserRepository userRepository,
            RoleRepository roleRepository,
            Keycloak keycloakAdminClient,
            @Value("${keycloak.realm}") String realm,
            @Value("${app.admin.email:}") String email,
            @Value("${app.admin.password:}") String password) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.keycloakAdminClient = keycloakAdminClient;
        this.realm = realm;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) {
            throw new IllegalStateException(
                    "BUYME_ADMIN_EMAIL and BUYME_ADMIN_PASSWORD must be configured to seed the admin account");
        }

        Role adminRole = roleRepository.findByName("ADMIN")
                .orElseGet(() -> roleRepository.save(new Role("ADMIN")));
        seedDatabaseUser(adminRole);
        seedKeycloakUser();
    }

    protected void seedDatabaseUser(Role adminRole) {
        // Existing rows seeded as the plain USER subtype are promoted to ADMIN first, so the
        // discriminator column matches the role granted below.
        int promoted = userRepository.promoteToAdmin(email);
        if (promoted > 0) {
            log.info("Promoted existing account {} to the ADMIN user type", email);
        }

        User user = userRepository.findByEmail(email).orElseGet(() -> {
            Admin newAdmin = new Admin();
            newAdmin.setEmail(email);
            newAdmin.setFirstName("BuyMe");
            newAdmin.setLastName("Administrator");
            return newAdmin;
        });

        user.setRoles(List.of(adminRole));
        userRepository.save(user);
        log.info("Seeded admin {} with roles {}",
                email, user.getRoles().stream().map(Role::getName).toList());
    }

    private void seedKeycloakUser() {
        var realmResource = keycloakAdminClient.realm(realm);
        var users = realmResource.users();
        List<UserRepresentation> matchingUsers = users.search(email).stream()
                .filter(user -> email.equalsIgnoreCase(user.getEmail())
                        || email.equalsIgnoreCase(user.getUsername()))
                .toList();

        if (matchingUsers.size() > 1) {
            throw new IllegalStateException("Multiple Keycloak users match the configured admin email");
        }

        String userId;
        if (matchingUsers.isEmpty()) {
            UserRepresentation user = new UserRepresentation();
            user.setUsername(email);
            user.setEmail(email);
            user.setFirstName("BuyMe");
            user.setLastName("Administrator");
            user.setEnabled(true);
            user.setEmailVerified(true);

            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(password);
            credential.setTemporary(false);
            user.setCredentials(List.of(credential));

            try (Response response = users.create(user)) {
                if (response.getStatus() != Response.Status.CREATED.getStatusCode()
                        || response.getLocation() == null) {
                    throw new IllegalStateException(
                            "Failed to create Keycloak admin user, status: " + response.getStatus());
                }
                String path = response.getLocation().getPath();
                userId = path.substring(path.lastIndexOf('/') + 1);
            }
        } else {
            userId = matchingUsers.get(0).getId();
        }

        RoleRepresentation adminRole = realmResource.roles().get("ADMIN").toRepresentation();
        var realmRoles = users.get(userId).roles().realmLevel();
        List<RoleRepresentation> assignedRoles = realmRoles.listAll();
        List<RoleRepresentation> clientRoles = assignedRoles.stream()
                .filter(role -> "USER".equals(role.getName()) || "CLIENT".equals(role.getName()))
                .toList();
        if (!clientRoles.isEmpty()) {
            realmRoles.remove(clientRoles);
        }

        boolean alreadyHasAdminRole = assignedRoles.stream()
                .anyMatch(role -> "ADMIN".equals(role.getName()));
        if (!alreadyHasAdminRole) {
            realmRoles.add(List.of(adminRole));
        }
    }
}
