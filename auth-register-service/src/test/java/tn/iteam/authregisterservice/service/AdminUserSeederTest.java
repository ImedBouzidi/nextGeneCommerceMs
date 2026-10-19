package tn.iteam.authregisterservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import tn.iteam.authregisterservice.model.Admin;
import tn.iteam.authregisterservice.model.Role;
import tn.iteam.authregisterservice.model.User;
import tn.iteam.authregisterservice.repos.RoleRepository;
import tn.iteam.authregisterservice.repos.UserRepository;

import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserSeederTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Keycloak keycloakAdminClient;

    @Test
    void seedsAdminProfileAndKeycloakCredentialWithoutStoringPlaintextPassword() {
        String email = "admin@example.com";
        String password = "strong-admin-password";
        Role adminRole = new Role(1L, "ADMIN");
        User existingUser = new User();
        existingUser.setEmail(email);
        existingUser.setRoles(List.of(new Role(2L, "USER")));
        RoleRepresentation keycloakAdminRole = new RoleRepresentation();
        Response response = mock(Response.class);

        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(adminRole));
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(existingUser));
        when(keycloakAdminClient.realm("e-commerce").users().search(email)).thenReturn(List.of());
        when(keycloakAdminClient.realm("e-commerce").users().create(any(UserRepresentation.class)))
                .thenReturn(response);
        when(response.getStatus()).thenReturn(Response.Status.CREATED.getStatusCode());
        when(response.getLocation()).thenReturn(URI.create("/users/keycloak-admin-id"));
        when(keycloakAdminClient.realm("e-commerce").roles().get("ADMIN").toRepresentation())
                .thenReturn(keycloakAdminRole);
        when(keycloakAdminClient.realm("e-commerce").users().get("keycloak-admin-id")
                .roles().realmLevel().listAll()).thenReturn(List.of());

        AdminUserSeeder seeder = new AdminUserSeeder(
                userRepository, roleRepository, keycloakAdminClient,
                "e-commerce", email, password);
        seeder.run(mock(ApplicationArguments.class));

        ArgumentCaptor<User> databaseUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(databaseUser.capture());
        assertEquals(email, databaseUser.getValue().getEmail());
        assertEquals(List.of("ADMIN"), databaseUser.getValue().getRoles().stream().map(Role::getName).toList());
        assertNull(databaseUser.getValue().getPassword());

        ArgumentCaptor<UserRepresentation> keycloakUser = ArgumentCaptor.forClass(UserRepresentation.class);
        verify(keycloakAdminClient.realm("e-commerce").users()).create(keycloakUser.capture());
        assertEquals(email, keycloakUser.getValue().getEmail());
        assertEquals(password, keycloakUser.getValue().getCredentials().get(0).getValue());
        verify(keycloakAdminClient.realm("e-commerce").users().get("keycloak-admin-id")
                .roles().realmLevel()).add(anyList());
    }

    @Test
    void removesClientRolesFromExistingAdminAndKeepsAdminRole() {
        String email = "admin@example.com";
        Role adminRole = new Role(1L, "ADMIN");
        RoleRepresentation keycloakAdminRole = new RoleRepresentation();
        keycloakAdminRole.setName("ADMIN");
        RoleRepresentation userRole = new RoleRepresentation();
        userRole.setName("USER");
        RoleRepresentation clientRole = new RoleRepresentation();
        clientRole.setName("CLIENT");
        UserRepresentation existingKeycloakUser = new UserRepresentation();
        existingKeycloakUser.setId("existing-admin-id");
        existingKeycloakUser.setEmail(email);

        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(adminRole));
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(new User()));
        when(keycloakAdminClient.realm("e-commerce").users().search(email))
                .thenReturn(List.of(existingKeycloakUser));
        when(keycloakAdminClient.realm("e-commerce").users().get("existing-admin-id")
                .roles().realmLevel().listAll())
                .thenReturn(List.of(userRole, clientRole, keycloakAdminRole));

        AdminUserSeeder seeder = new AdminUserSeeder(
                userRepository, roleRepository, keycloakAdminClient,
                "e-commerce", email, "strong-admin-password");
        seeder.run(mock(ApplicationArguments.class));

        verify(keycloakAdminClient.realm("e-commerce").users().get("existing-admin-id")
                .roles().realmLevel()).remove(List.of(userRole, clientRole));
        verify(keycloakAdminClient.realm("e-commerce").users().get("existing-admin-id")
                .roles().realmLevel(), never()).add(anyList());
        verify(keycloakAdminClient.realm("e-commerce").users(), never()).create(any(UserRepresentation.class));
    }

    @Test
    void promotesExistingAccountToAdminTypeAndCreatesAdminSubtypeWhenMissing() {
        String email = "admin@example.com";
        Role adminRole = new Role(1L, "ADMIN");
        RoleRepresentation keycloakAdminRole = new RoleRepresentation();
        keycloakAdminRole.setName("ADMIN");
        UserRepresentation existingKeycloakUser = new UserRepresentation();
        existingKeycloakUser.setId("existing-admin-id");
        existingKeycloakUser.setEmail(email);

        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(adminRole));
        when(userRepository.promoteToAdmin(email)).thenReturn(1);
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(keycloakAdminClient.realm("e-commerce").users().search(email))
                .thenReturn(List.of(existingKeycloakUser));
        when(keycloakAdminClient.realm("e-commerce").users().get("existing-admin-id")
                .roles().realmLevel().listAll())
                .thenReturn(List.of(keycloakAdminRole));

        AdminUserSeeder seeder = new AdminUserSeeder(
                userRepository, roleRepository, keycloakAdminClient,
                "e-commerce", email, "strong-admin-password");
        seeder.run(mock(ApplicationArguments.class));

        verify(userRepository).promoteToAdmin(email);
        ArgumentCaptor<User> databaseUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(databaseUser.capture());
        assertInstanceOf(Admin.class, databaseUser.getValue());
        assertEquals(List.of("ADMIN"),
                databaseUser.getValue().getRoles().stream().map(Role::getName).toList());
    }
}
