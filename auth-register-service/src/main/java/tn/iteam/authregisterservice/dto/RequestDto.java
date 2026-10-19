package tn.iteam.authregisterservice.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RequestDto {
    @JsonProperty("email")
    private String email;

    @JsonProperty("password")
    private String password;
}

