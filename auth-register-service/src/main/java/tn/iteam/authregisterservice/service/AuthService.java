
package tn.iteam.authregisterservice.service;

import lombok.extern.slf4j.Slf4j;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;
import tn.iteam.authregisterservice.dto.*;
import tn.iteam.authregisterservice.exceptions.EmailAlreadyExistsException;
import tn.iteam.authregisterservice.interfaces.FileStorageService;
import tn.iteam.authregisterservice.model.Client;
import tn.iteam.authregisterservice.model.Role;
import tn.iteam.authregisterservice.model.User;
import tn.iteam.authregisterservice.repos.RoleRepository;
import tn.iteam.authregisterservice.repos.UserRepository;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final FileStorageService fileStorageService;

    @Value("${keycloak.auth-server-url}")
    private String keycloakUrl;
    @Value("${keycloak.realm}")
    private String realm;
    @Value("${keycloak.resource}")
    private String clientId;
    @Value("${keycloak.credentials.secret}")
    private String clientSecret;
    private final Keycloak keycloakAdminClient;
    private final RestClient restClient = RestClient.create();
    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       FileStorageService fileStorageService,
                       Keycloak keycloakAdminClient) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.fileStorageService = fileStorageService;
        this.keycloakAdminClient = keycloakAdminClient;
    }

    // --------------------------------------------------------------------
    // REGISTER USER
    // --------------------------------------------------------------------
    @Transactional
    public RegistrationResponseDto register(RegisterRequestDto dto) {
        log.info("Registering new client: {}", dto.getEmail());

        if (userRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new EmailAlreadyExistsException("Email already exists: " + dto.getEmail());
        }

        Client client = new Client();
        client.setFirstName(dto.getFirstName());
        client.setLastName(dto.getLastName());
        client.setEmail(dto.getEmail());
        client.setPhone(Integer.valueOf(dto.getPhone()));
        client.setDob(LocalDate.parse(dto.getDob()));
        client.setCountry(dto.getCountry());
        client.setCity(dto.getCity());
        client.setZip(Integer.valueOf(dto.getZip()));
        client.setAddress(dto.getAddress());
        if (dto.getProfilePicFile() != null && !dto.getProfilePicFile().isEmpty()) {
            try {
                String filename = fileStorageService.storeFile(dto.getProfilePicFile(), "profile-pics");
                client.setProfilePic(filename);
            } catch (IOException e) {
                throw new RuntimeException("Failed to store profile picture", e);
            }
        }
        Role clientRole = roleRepository.findByName("CLIENT")
                .orElseGet(() -> roleRepository.save(new Role("CLIENT")));
        client.setRoles(List.of(clientRole));
        client = userRepository.save(client);
        // Keycloak user creation
        createKeycloakUser(client, dto.getPassword());
        return new RegistrationResponseDto(
                client.getRoles().stream().map(Role::getName).toList(),
                "Client registered successfully"
        );
    }


    // --------------------------------------------------------------------
    // LOGIN (local lookup only, auth handled by Keycloak)
    // --------------------------------------------------------------------
    /*public LoginResponseDto login(RequestDto dto) {
        Optional<User> userOpt = userRepository.findByEmail(dto.getEmail());
        if (userOpt.isEmpty()) {
            return new LoginResponseDto(null, null, "User not found");
        }
        User user = userOpt.get();

        // --- Authenticate with Keycloak ---
        try {
            Keycloak keycloak = KeycloakBuilder.builder()
                    .serverUrl(keycloakUrl)
                    .realm(realm)
                    .clientId(clientId)
                    .clientSecret(clientSecret)
                    .username(dto.getEmail())
                    .password(dto.getPassword())
                    .build();

            String token = keycloak.tokenManager().getAccessTokenString();

            LoginResponseDto response = new LoginResponseDto();
            response.setRoles(user.getRoles().stream().map(Role::getName).toList());
            response.setToken(token);
            response.setMessage("Login successful with Keycloak");

            return response;
        } catch (Exception e) {
            // The 3-arg constructor of LoginResponseDto is (token, roles, message).
            return new LoginResponseDto(null, null, "Invalid credentials or Keycloak error");
        }
    }
*/

    public LoginResponseDto login(RequestDto dto) {
        Optional<User> userOpt = userRepository.findByEmail(dto.getEmail());
        if (userOpt.isEmpty()) {
            return new LoginResponseDto(null, null, "User not found");
        }
        User user = userOpt.get();

        try {
            Keycloak keycloak = KeycloakBuilder.builder()
                    .serverUrl(keycloakUrl)
                    .realm(realm)
                    .clientId(clientId)
                    .clientSecret(clientSecret)
                    .username(dto.getEmail())
                    .password(dto.getPassword())
                    .grantType(OAuth2Constants.PASSWORD)
                    .build();

            AccessTokenResponse tokenResponse = keycloak.tokenManager().getAccessToken();
            String token = tokenResponse != null ? tokenResponse.getToken() : null;
            if (token == null || token.isBlank() || !isJwt(token)) {
                log.warn("Keycloak returned no usable access token for {}", dto.getEmail());
                return new LoginResponseDto(null, null, "Invalid credentials or Keycloak error");
            }

            // Ensure Keycloak roles match local database roles
            syncKeycloakRoles(keycloak, user);

            LoginResponseDto response = new LoginResponseDto();
            response.setRoles(user.getRoles().stream().map(Role::getName).toList());
            response.setToken(token);
            response.setRefreshToken(tokenResponse.getRefreshToken());
            response.setExpiresIn(tokenResponse.getExpiresIn());
            response.setMessage("Login successful with Keycloak");

            return response;

        } catch (Exception e) {
            log.warn("Keycloak login failed for {}: {}", dto.getEmail(), e.getMessage());
            return new LoginResponseDto(null, null, "Invalid credentials or Keycloak error");
        }
    }

    /**
     * Exchanges a Keycloak refresh token for a new access token.
     *
     * Access tokens live five minutes by default, which is far shorter than a
     * typical admin editing session. The browser calls this instead of forcing
     * the user to sign in again. Keycloak may rotate the refresh token, so the
     * new one is returned as well.
     */
    public LoginResponseDto refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return new LoginResponseDto(null, null, "Missing refresh token");
        }

        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", OAuth2Constants.REFRESH_TOKEN);
            form.add("client_id", clientId);
            form.add("client_secret", clientSecret);
            form.add("refresh_token", refreshToken);

            Map<String, Object> body = restClient.post()
                    .uri(keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            Object accessToken = body != null ? body.get("access_token") : null;
            if (accessToken == null || !isJwt(accessToken.toString())) {
                log.warn("Keycloak refresh returned no usable access token");
                return new LoginResponseDto(null, null, "Could not refresh session");
            }

            Object nextRefreshToken = body.get("refresh_token");
            Object expiresIn = body.get("expires_in");

            LoginResponseDto response = new LoginResponseDto();
            response.setToken(accessToken.toString());
            response.setRefreshToken(nextRefreshToken != null ? nextRefreshToken.toString() : refreshToken);
            response.setExpiresIn(expiresIn instanceof Number number ? number.longValue() : null);
            response.setMessage("Session refreshed");
            return response;

        } catch (RestClientResponseException e) {
            log.warn("Keycloak refresh failed: HTTP {}", e.getStatusCode().value());
            return new LoginResponseDto(null, null, "Session expired, please sign in again");
        } catch (Exception e) {
            log.warn("Keycloak refresh failed: {}", e.getMessage());
            return new LoginResponseDto(null, null, "Session expired, please sign in again");
        }
    }

    /**
     * Synchronizes the user's realm roles in Keycloak with their local database roles.
     * This ensures @PreAuthorize checks in other services work correctly.
     */
    private void syncKeycloakRoles(Keycloak keycloak, User user) {
        try {
            var realmResource = keycloak.realm(realm);
            var users = realmResource.users();
            
            // Find user in Keycloak
            List<UserRepresentation> kcUsers = users.search(user.getEmail()).stream()
                    .filter(u -> user.getEmail().equalsIgnoreCase(u.getEmail()) 
                            || user.getEmail().equalsIgnoreCase(u.getUsername()))
                    .toList();

            if (kcUsers.isEmpty()) {
                log.warn("User {} not found in Keycloak, cannot sync roles", user.getEmail());
                return;
            }

            String userId = kcUsers.get(0).getId();
            var realmRoles = users.get(userId).roles().realmLevel();
            
            // Get current assigned roles
            List<RoleRepresentation> assignedRoles = realmRoles.listAll();
            
            // Remove default/client roles we don't want (keep only realm-level custom roles)
            List<RoleRepresentation> defaultRoles = assignedRoles.stream()
                    .filter(role -> ("default-roles-" + realm).equals(role.getName()) 
                            || "USER".equals(role.getName()) 
                            || "CLIENT".equals(role.getName()))
                    .toList();
            if (!defaultRoles.isEmpty()) {
                realmRoles.remove(defaultRoles);
            }

            // Add missing roles from local database
            List<String> localRoleNames = user.getRoles().stream()
                    .map(Role::getName)
                    .map(String::toUpperCase)
                    .toList();

            for (String roleName : localRoleNames) {
                boolean alreadyHasRole = assignedRoles.stream()
                        .anyMatch(r -> roleName.equalsIgnoreCase(r.getName()));
                
                if (!alreadyHasRole) {
                    try {
                        RoleRepresentation kcRole = realmResource.roles().get(roleName).toRepresentation();
                        realmRoles.add(List.of(kcRole));
                        log.info("Assigned {} role to user {} in Keycloak", roleName, user.getEmail());
                    } catch (Exception e) {
                        log.warn("Role {} does not exist in Keycloak realm {}", roleName, realm);
                    }
                }
            }

        } catch (Exception e) {
            log.warn("Failed to sync Keycloak roles for user {}: {}", user.getEmail(), e.getMessage());
        }
    }



    // --------------------------------------------------------------------
    // GET CURRENT USER (from JWT token)
    // --------------------------------------------------------------------
    /**
     * Guards against a non-JWT value ending up in the {@code token} field.
     * The Keycloak admin client's token manager returns the HTTP status as a
     * string when no token could be obtained, which previously reached the
     * browser as a "token" and was stored as a bearer credential.
     */
    private boolean isJwt(String token) {
        return token.split("\\.").length == 3;
    }

    public User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token) {

            String email = token.getToken().getClaimAsString("email");
            return userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found: " + email));
        }
        throw new RuntimeException("No authenticated user found");
    }
    // --------------------------------------------------------------------
    // CREATE USER IN KEYCLOAK
    // --------------------------------------------------------------------
    private void createKeycloakUser(Client client, String password) {
        try {
            // Check if exists
            List<UserRepresentation> existingUsers = keycloakAdminClient.realm(realm)
                    .users().search(client.getEmail());

            if (!existingUsers.isEmpty()) {
                throw new EmailAlreadyExistsException(
                        client.getEmail());
            }
            UserRepresentation user = toKeycloakUser(client, password);
            // Create user
            String userId;
            try (Response res = keycloakAdminClient.realm(realm).users().create(user)) {
                if (res.getStatus() != 201 || res.getLocation() == null) {
                    throw new IllegalStateException(
                            "Failed to create Keycloak user, status: " + res.getStatus());
                }
                userId = res.getLocation().getPath().replaceAll(".*/([^/]+)$", "$1");
            }
            // Assign role CLIENT
            RoleRepresentation kcRole = keycloakAdminClient.realm(realm)
                    .roles().get("CLIENT").toRepresentation();

            keycloakAdminClient.realm(realm).users().get(userId)
                    .roles().realmLevel().add(List.of(kcRole));

            log.info("CLIENT role assigned to user {}", client.getEmail());

        } catch (Exception e) {
            throw new RuntimeException("Keycloak user creation failed: " + e.getMessage(), e);
        }
    }

    static UserRepresentation toKeycloakUser(Client client, String password) {
        UserRepresentation user = new UserRepresentation();
        user.setUsername(client.getEmail());
        user.setEmail(client.getEmail());
        user.setFirstName(client.getFirstName());
        user.setLastName(client.getLastName());
        user.setEnabled(true);
        user.setEmailVerified(true);

        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(false);
        user.setCredentials(Collections.singletonList(credential));
        return user;
    }

    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email).orElse(null);
    }
}
