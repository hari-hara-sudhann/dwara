package me.hari.dwara.services;

import lombok.RequiredArgsConstructor;
import me.hari.dwara.dtos.ResponseObject;
import me.hari.dwara.dtos.user.RegisrationSuccessDto;
import me.hari.dwara.dtos.user.RegistrationDto;
import me.hari.dwara.entities.User;
import me.hari.dwara.entities.enums.Role;
import me.hari.dwara.repositories.UserRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {
    private UserRepository userRepository;

    public ResponseObject<RegisrationSuccessDto> register(RegistrationDto dto) {
        ResponseObject<RegisrationSuccessDto> responseDto = new ResponseObject<>();
        if (userRepository.existsByEmail(dto.getEmail())) responseDto.setResponse(null);

        User newUser = new User();
        newUser.setName(dto.getEmail());
        newUser.setEmail(dto.getEmail());

        // TODO: Replace this after creating password hash in security config.
        newUser.setPasswordHash(dto.getRawPassword());
        // -------

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
}
