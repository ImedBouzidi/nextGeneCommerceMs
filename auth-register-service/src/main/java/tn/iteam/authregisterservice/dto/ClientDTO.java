package tn.iteam.authregisterservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ClientDTO {
    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private Integer phone; // changed from int
    private Date dob;
    private String country;
    private String city;
    private Integer zip; // changed from int
    private String address;
    //private String password;
    private String profilePic;
}

