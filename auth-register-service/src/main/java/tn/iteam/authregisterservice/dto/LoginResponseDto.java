package tn.iteam.authregisterservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponseDto {
    /**
     * The Keycloak access token, or null when authentication failed.
     * The all-args constructor is (token, roles, message) — AuthController
     * treats a null token as a failed login and answers 401.
     */
    private String token;
    private List<String> roles;
    private String message;
}
