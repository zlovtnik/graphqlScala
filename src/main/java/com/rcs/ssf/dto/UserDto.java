package com.rcs.ssf.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private UUID id;
    private String username;
    private String email;

    public static UserDto from(com.rcs.ssf.entity.User user) {
        return new UserDto(user.getId(), user.getUsername(), user.getEmail());
    }
}