package me.hari.dwara.controllers;

import lombok.RequiredArgsConstructor;
import me.hari.dwara.dtos.ResponseObject;
import me.hari.dwara.dtos.user.LoginRequestDto;
import me.hari.dwara.dtos.user.LoginResponseDto;
import me.hari.dwara.dtos.user.RegisrationSuccessDto;
import me.hari.dwara.dtos.user.RegistrationDto;
import me.hari.dwara.services.UserService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public identity endpoints (spec §5). Registration forces the {@code USER}
 * role (D13); login returns the JWT used by every other app endpoint.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseObject<RegisrationSuccessDto> register(@RequestBody RegistrationDto dto) {
        return userService.register(dto);
    }

    @PostMapping("/login")
    public ResponseObject<LoginResponseDto> login(@RequestBody LoginRequestDto dto) {
        return userService.login(dto);
    }
}
