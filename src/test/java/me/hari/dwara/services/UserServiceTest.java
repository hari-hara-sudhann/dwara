package me.hari.dwara.services;

import me.hari.dwara.dtos.ResponseObject;
import me.hari.dwara.dtos.user.LoginRequestDto;
import me.hari.dwara.dtos.user.LoginResponseDto;
import me.hari.dwara.dtos.user.RegisrationSuccessDto;
import me.hari.dwara.dtos.user.RegistrationDto;
import me.hari.dwara.entities.User;
import me.hari.dwara.entities.enums.UserRole;
import me.hari.dwara.repositories.UserRepository;
import me.hari.dwara.utilities.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private JwtService jwtService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(jwtService, userRepository, passwordEncoder);
    }

    // ------------------------------------------------------------ register --

    @Test
    void register_refusesAnEmailThatIsAlreadyTaken() {
        when(userRepository.existsByEmail("a@b.c")).thenReturn(true);

        RegistrationDto dto = registration("a@b.c", "hunter2");
        ResponseObject<RegisrationSuccessDto> result = service.register(dto);

        assertFalse(result.getSuccess());
        assertEquals("User already exists.", result.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_hashesThePasswordAndForcesTheUserRole() {
        when(userRepository.existsByEmail("a@b.c")).thenReturn(false);
        when(passwordEncoder.encode("hunter2")).thenReturn("BCRYPT-HASH");

        ResponseObject<RegisrationSuccessDto> result = service.register(registration("a@b.c", "hunter2"));

        assertTrue(result.getSuccess());
        assertEquals("a@b.c", result.getResponse().getEmail());
        assertEquals("a@b.c", result.getResponse().getName());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals("BCRYPT-HASH", saved.getValue().getPasswordHash());
        assertEquals(UserRole.USER, saved.getValue().getUserRole());
    }

    @Test
    void register_storesTheNameTheUserSupplied() {
        when(userRepository.existsByEmail("a@b.c")).thenReturn(false);
        when(passwordEncoder.encode("hunter2")).thenReturn("BCRYPT-HASH");
        RegistrationDto dto = registration("a@b.c", "hunter2");
        dto.setName("Verify User");

        ResponseObject<RegisrationSuccessDto> result = service.register(dto);

        assertEquals("Verify User", result.getResponse().getName());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals("Verify User", saved.getValue().getName());
    }

    @Test
    void register_fallsBackToEmailWhenNoNameIsSupplied() {
        when(userRepository.existsByEmail("a@b.c")).thenReturn(false);
        when(passwordEncoder.encode("hunter2")).thenReturn("BCRYPT-HASH");
        RegistrationDto dto = registration("a@b.c", "hunter2");
        dto.setName("  ");

        ResponseObject<RegisrationSuccessDto> result = service.register(dto);

        assertEquals("a@b.c", result.getResponse().getName());
    }

    // --------------------------------------------------------------- login --

    @Test
    void login_refusesAnUnknownEmail() {
        when(userRepository.findByEmail("ghost@b.c")).thenReturn(null);

        LoginRequestDto dto = login("ghost@b.c", "hunter2");
        ResponseObject<LoginResponseDto> result = service.login(dto);

        assertFalse(result.getSuccess());
        assertEquals("Invalid credentials.", result.getMessage());
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void login_refusesAWrongPassword() {
        User user = user("BCRYPT-HASH");
        when(userRepository.findByEmail("a@b.c")).thenReturn(user);
        when(passwordEncoder.matches("wrong", "BCRYPT-HASH")).thenReturn(false);

        ResponseObject<LoginResponseDto> result = service.login(login("a@b.c", "wrong"));

        assertFalse(result.getSuccess());
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void login_issuesAJwtForValidCredentials() {
        User user = user("BCRYPT-HASH");
        when(userRepository.findByEmail("a@b.c")).thenReturn(user);
        when(passwordEncoder.matches("hunter2", "BCRYPT-HASH")).thenReturn(true);
        when(jwtService.generateToken(USER_ID)).thenReturn("signed.jwt");

        ResponseObject<LoginResponseDto> result = service.login(login("a@b.c", "hunter2"));

        assertTrue(result.getSuccess());
        assertEquals("signed.jwt", result.getResponse().jwt());
    }

    // ------------------------------------------------------------- findUser --

    @Test
    void findUser_returnsTheProfileBehindTheToken() {
        User user = user("BCRYPT-HASH");
        user.setName("Hari");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        ResponseObject<RegisrationSuccessDto> result = service.findUser(USER_ID);

        assertTrue(result.getSuccess());
        assertEquals(USER_ID, result.getResponse().getUserId());
        assertEquals("Hari", result.getResponse().getName());
    }

    @Test
    void findUser_refusesATokenForADeletedUser() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertFalse(service.findUser(USER_ID).getSuccess());
    }

    // ------------------------------------------------------------- helpers --

    private static RegistrationDto registration(String email, String rawPassword) {
        RegistrationDto dto = new RegistrationDto();
        dto.setEmail(email);
        dto.setName(email);
        dto.setRawPassword(rawPassword);
        return dto;
    }

    private static LoginRequestDto login(String email, String password) {
        LoginRequestDto dto = new LoginRequestDto();
        dto.setEmail(email);
        dto.setPassword(password);
        return dto;
    }

    private static User user(String passwordHash) {
        User user = new User();
        user.setUserId(USER_ID);
        user.setEmail("a@b.c");
        user.setPasswordHash(passwordHash);
        return user;
    }
}
