package me.hari.dwara.services;

import lombok.RequiredArgsConstructor;
import me.hari.dwara.dtos.ResponseObject;
import me.hari.dwara.dtos.user.LoginRequestDto;
import me.hari.dwara.dtos.user.LoginResponseDto;
import me.hari.dwara.dtos.user.RegisrationSuccessDto;
import me.hari.dwara.dtos.user.RegistrationDto;
import me.hari.dwara.entities.User;
import me.hari.dwara.entities.enums.UserRole;
import me.hari.dwara.mappers.UserMapper;
import me.hari.dwara.repositories.UserRepository;
import me.hari.dwara.utilities.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class UserService {
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public ResponseObject<RegisrationSuccessDto> register(RegistrationDto dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            return new ResponseObject<>(false, "User already exists.", null);
        }

        User newUser = new User();
        newUser.setName(dto.getEmail());
        newUser.setEmail(dto.getEmail());

        String passwordHash = passwordEncoder.encode(dto.getRawPassword());
        newUser.setPasswordHash(passwordHash);

        newUser.setUserRole(UserRole.USER);

        userRepository.save(newUser);

        var response = UserMapper.toRegistrationSuccessDto(newUser);
        return new ResponseObject<>(true, "Registered successfully.", response);
    }

    public ResponseObject<LoginResponseDto> login(LoginRequestDto dto) {
        User user = userRepository.findByEmail(dto.getEmail());
        if (user == null || !verify(dto.getPassword(), user.getPasswordHash())) {
            return new ResponseObject<>(false, "Invalid credentials.", null);
        }

        String jwt = jwtService.generateToken(user.getUserId());
        LoginResponseDto res = new LoginResponseDto(jwt);

        return new ResponseObject<>(true, "Login successful.", res);
    }

    private boolean verify(String raw, String hashed) {
        return passwordEncoder.matches(raw, hashed);
    }
}
