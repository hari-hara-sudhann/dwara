package me.hari.dwara.dtos.user;

import lombok.Data;

import java.util.UUID;

@Data
public class RegisrationSuccessDto {
    private UUID userId;
    private String name;
    private String email;
}
