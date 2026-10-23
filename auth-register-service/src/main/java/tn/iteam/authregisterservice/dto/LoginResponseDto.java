package tn.iteam.authregisterservice.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class LoginResponseDto {
    /**
     * The Keycloak access token, or null when authentication failed.
     * The (token, roles, message) constructor is used for failures — AuthController
     * treats a null token as a failed login and answers 401.
     */
    private String token;
    private List<String> roles;
    private String message;

    /**
     * Keycloak refresh token. Only issued on login; never returned for failures.
     * The browser exchanges it for a new access token when the short-lived one
     * expires (see POST /api/auth/refresh).
     */
    private String refreshToken;

    /** Access token lifetime in seconds, as reported by Keycloak. */
    private Long expiresIn;

    public LoginResponseDto(String token, List<String> roles, String message) {
        this.token = token;
        this.roles = roles;
        this.message = message;
    }
}
