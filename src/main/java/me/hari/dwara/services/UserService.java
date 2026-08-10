package me.hari.dwara.services;

import lombok.RequiredArgsConstructor;
import me.hari.dwara.dtos.ResponseObject;
import me.hari.dwara.dtos.user.LoginRequestDto;
import me.hari.dwara.dtos.user.LoginResponseDto;
import me.hari.dwara.dtos.user.RegisrationSuccessDto;
import me.hari.dwara.dtos.user.RegistrationDto;
import me.hari.dwara.entities.User;
import me.hari.dwara.entities.enums.Role;
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
        ResponseObject<RegisrationSuccessDto> responseDto = new ResponseObject<>();
        if (userRepository.existsByEmail(dto.getEmail())) {
            responseDto.setResponse(null);
            responseDto.setMessage("User Already Exists.");
            responseDto.setSuccess(false);
            return responseDto;
        }

        User newUser = new User();
        newUser.setName(dto.getEmail());
        newUser.setEmail(dto.getEmail());

        String passwordHash = passwordEncoder.encode(dto.getRawPassword());
        newUser.setPasswordHash(passwordHash);

        newUser.setRole(Role.USER);

        userRepository.save(newUser);


        var response = new RegisrationSuccessDto();
        response.setName(newUser.getName());
        response.setEmail(newUser.getEmail());
        response.setUserId(
                userRepository.findByEmail(newUser.getEmail())
                              .getUserId()
        );
        responseDto.setResponse(response);

        return responseDto;
    }

    public ResponseObject<LoginResponseDto> login(LoginRequestDto dto) {
        ResponseObject<LoginResponseDto> obj = new ResponseObject<>();

        User user = userRepository.findByEmail(dto.getEmail());
        if (user == null || !verify(dto.getPassword(), user.getPasswordHash())) {
            obj.setSuccess(false);
            obj.setMessage("Invalid credentials.");
            return obj;
        }

        String jwt = jwtService.generateToken(user.getUserId());
        LoginResponseDto res = new LoginResponseDto(jwt);

        obj.setSuccess(true);
        obj.setMessage("Login successful.");
        obj.setResponse(res);

        return obj;
    }

    private boolean verify(String raw, String hashed) {
        return passwordEncoder.matches(raw, hashed);
    }
}
