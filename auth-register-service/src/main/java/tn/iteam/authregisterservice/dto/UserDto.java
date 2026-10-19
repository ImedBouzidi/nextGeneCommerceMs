package tn.iteam.authregisterservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "User Data Transfer Object used for registration and profile")
public class UserDto {

    @Schema(description = "User ID (auto-generated, ignored on registration)", example = "1", accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;

    @Schema(description = "First name of the user", example = "Imed", required = true)
    @NotEmpty(message = "First name is required")
    private String firstName;

    @Schema(description = "Last name of the user", example = "Bouzidi", required = true)
    @NotEmpty(message = "Last name is required")
    private String lastName;

    @Schema(description = "Email address of the user", example = "imed.bouzidi@example.com", required = true)
    @NotEmpty(message = "Email is required")
    private String email;

    @Schema(description = "Password of the user (min 8 characters)", example = "secret1234", required = true)
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @Schema(description = "Phone number", example = "123456789")
    private Integer phone;

    @Schema(description = "Date of birth in ISO format", example = "1995-08-20")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dob;

    @Schema(description = "Country", example = "Tunisia")
    private String country;

    @Schema(description = "City", example = "Sousse")
    private String city;

    @Schema(description = "ZIP code", example = "4000")
    private Integer zip;

    @Schema(description = "Address", example = "123 Main Street")
    private String address;

    @Schema(description = "Stored profile picture filename (read-only)", example = "profile_1234.jpg", accessMode = Schema.AccessMode.READ_ONLY)
    private String profilePic;

    @Schema(description = "Profile picture file for upload (multipart)", type = "string", format = "binary")
    private MultipartFile profilePicFile;

    @Schema(description = "List of roles assigned to the user", example = "[\"CLIENT\"]", accessMode = Schema.AccessMode.READ_ONLY)
    private List<String> roles;
}
