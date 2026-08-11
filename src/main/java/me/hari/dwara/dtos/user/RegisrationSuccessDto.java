package me.hari.dwara.dtos.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RegisrationSuccessDto {
    private UUID userId;
    private String name;
    private String email;
}
