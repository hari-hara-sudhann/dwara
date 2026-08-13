package me.hari.dwara.dtos.user;

import lombok.Data;

@Data
public class RegistrationDto {
    private String email;
    private String name;
    private String rawPassword;
}
