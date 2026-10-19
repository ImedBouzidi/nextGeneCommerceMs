package tn.iteam.authregisterservice.mappers;

import tn.iteam.authregisterservice.dto.UserDto;
import tn.iteam.authregisterservice.model.Client;
import tn.iteam.authregisterservice.model.Role;
import tn.iteam.authregisterservice.model.User;

import java.util.stream.Collectors;

public final class UserMapper {

    private UserMapper() {}

    public static UserDto toDto(User user) {
        if (user == null) return null;

        return UserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phone(user.getPhone())
                .dob(user.getDob())
                .country(user.getCountry())
                .city(user.getCity())
                .zip(user.getZip())
                .address(user.getAddress())
                .profilePic(user.getProfilePic())
                .roles(user.getRoles().stream()
                        .map(Role::getName)
                        .collect(Collectors.toList()))
                .build();
    }

    public static Client toClientEntity(UserDto dto) {
        if (dto == null) return null;

        Client client = new Client();
        client.setEmail(dto.getEmail());
        client.setFirstName(dto.getFirstName());
        client.setLastName(dto.getLastName());
        client.setPhone(dto.getPhone());
        client.setDob(dto.getDob());
        client.setCountry(dto.getCountry());
        client.setCity(dto.getCity());
        client.setZip(dto.getZip());
        client.setAddress(dto.getAddress());
        client.setProfilePic(dto.getProfilePic());
        // password & roles set in service
        return client;
    }
}