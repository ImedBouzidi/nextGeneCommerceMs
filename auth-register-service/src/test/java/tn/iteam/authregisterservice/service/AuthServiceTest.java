package tn.iteam.authregisterservice.service;

import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.UserRepresentation;
import tn.iteam.authregisterservice.model.Client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceTest {

    @Test
    void newKeycloakUsersAreEnabledAndEmailVerified() {
        Client client = new Client();
        client.setEmail("customer@example.com");
        client.setFirstName("Customer");
        client.setLastName("Example");

        UserRepresentation user = AuthService.toKeycloakUser(client, "user-password");

        assertEquals("customer@example.com", user.getEmail());
        assertTrue(user.isEnabled());
        assertTrue(user.isEmailVerified());
        assertEquals("user-password", user.getCredentials().get(0).getValue());
    }
}
