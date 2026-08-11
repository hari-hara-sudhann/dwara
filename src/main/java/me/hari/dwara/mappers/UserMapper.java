package me.hari.dwara.mappers;


import me.hari.dwara.dtos.user.RegisrationSuccessDto;
import me.hari.dwara.entities.User;

public class UserMapper {
    public static RegisrationSuccessDto toRegistrationSuccessDto(User user) {
        return new RegisrationSuccessDto(
                user.getUserId(),
                user.getName(),
                user.getEmail()
        );
    }

}
