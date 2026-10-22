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

import java.util.UUID;


@Service
@RequiredArgsConstructor
public class UserService {
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public ResponseObject<RegisrationSuccessDto> register(RegistrationDto dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            return ResponseObject.failure("User already exists.");
        }

        User newUser = new User();
        newUser.setName(dto.getName() != null && !dto.getName().isBlank()
                ? dto.getName()
                : dto.getEmail());
        newUser.setEmail(dto.getEmail());

        String passwordHash = passwordEncoder.encode(dto.getRawPassword());
        newUser.setPasswordHash(passwordHash);

        newUser.setUserRole(UserRole.USER);

        userRepository.save(newUser);

        var response = UserMapper.toRegistrationSuccessDto(newUser);
        return ResponseObject.success( "Registered successfully.", response);
    }

    public ResponseObject<LoginResponseDto> login(LoginRequestDto dto) {
        User user = userRepository.findByEmail(dto.getEmail());
        if (user == null || !verify(dto.getPassword(), user.getPasswordHash())) {
            return ResponseObject.failure("Invalid credentials.");
        }

        String jwt = jwtService.generateToken(user.getUserId());
        LoginResponseDto res = new LoginResponseDto(jwt);

        return ResponseObject.success( "Login successful.", res);
    }

    /**
     * Identity behind a JWT — used by {@code GET /auth/me} so the app can
     * restore its session after a relaunch (the JWT only carries the user id).
     */
    public ResponseObject<RegisrationSuccessDto> findUser(UUID userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseObject.failure("User does not exist.");
        }
        return ResponseObject.success("User found.", UserMapper.toRegistrationSuccessDto(user));
    }

    private boolean verify(String raw, String hashed) {
        return passwordEncoder.matches(raw, hashed);
    }
}
