package tn.iteam.authregisterservice.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;
@Getter
@Setter
public class RegisterRequestDto {
    private String firstName;
    private String lastName;
    private String email;
    private String password;
    private String phone;
    private String dob;
    private String country;
    private String city;
    private String zip;
    private String address;
    private MultipartFile profilePicFile;
}
