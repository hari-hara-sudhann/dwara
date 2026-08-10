package me.hari.dwara.dtos.user;

import lombok.Data;

@Data
public class LoginRequestDto {
    private String email;
    private String password;
}
