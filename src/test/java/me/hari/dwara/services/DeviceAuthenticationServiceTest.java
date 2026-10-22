package me.hari.dwara.services;

import me.hari.dwara.entities.GateController;
import me.hari.dwara.repositories.GateControllerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceAuthenticationServiceTest {

    private static final UUID DEVICE_ID = UUID.randomUUID();
    private static final String SECRET = "flashed-device-secret";
    private static final String HASH = "BCRYPT-HASH";

    @Mock
    private GateControllerRepository gateControllerRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private DeviceAuthenticationService service;

    @BeforeEach
    void setUp() {
        service = new DeviceAuthenticationService(gateControllerRepository, passwordEncoder);
    }

    @Test
    void authenticate_refusesAnUnknownDevice() {
        when(gateControllerRepository.findById(DEVICE_ID)).thenReturn(Optional.empty());

        assertThrows(InvalidDeviceCredentialsException.class,
                () -> service.authenticate(DEVICE_ID, "Bearer " + SECRET));

        verify(passwordEncoder, never()).matches(SECRET, HASH);
    }

    @Test
    void authenticate_refusesAMissingAuthorizationHeader() {
        when(gateControllerRepository.findById(DEVICE_ID)).thenReturn(Optional.of(device(HASH)));

        assertThrows(InvalidDeviceCredentialsException.class,
                () -> service.authenticate(DEVICE_ID, null));
    }

    @Test
    void authenticate_refusesANonBearerAuthorizationHeader() {
        when(gateControllerRepository.findById(DEVICE_ID)).thenReturn(Optional.of(device(HASH)));

        assertThrows(InvalidDeviceCredentialsException.class,
                () -> service.authenticate(DEVICE_ID, "Basic " + SECRET));
    }

    @Test
    void authenticate_refusesAnEmptyBearerToken() {
        when(gateControllerRepository.findById(DEVICE_ID)).thenReturn(Optional.of(device(HASH)));

        assertThrows(InvalidDeviceCredentialsException.class,
                () -> service.authenticate(DEVICE_ID, "Bearer   "));
    }

    @Test
    void authenticate_refusesADeviceWithoutAProvisionedSecret() {
        when(gateControllerRepository.findById(DEVICE_ID)).thenReturn(Optional.of(device(null)));

        assertThrows(InvalidDeviceCredentialsException.class,
                () -> service.authenticate(DEVICE_ID, "Bearer " + SECRET));
    }

    @Test
    void authenticate_refusesAWrongSecret() {
        when(gateControllerRepository.findById(DEVICE_ID)).thenReturn(Optional.of(device(HASH)));
        when(passwordEncoder.matches(SECRET, HASH)).thenReturn(false);

        assertThrows(InvalidDeviceCredentialsException.class,
                () -> service.authenticate(DEVICE_ID, "Bearer " + SECRET));
    }

    @Test
    void authenticate_acceptsTheFlashedSecret() {
        GateController device = device(HASH);
        when(gateControllerRepository.findById(DEVICE_ID)).thenReturn(Optional.of(device));
        when(passwordEncoder.matches(SECRET, HASH)).thenReturn(true);

        assertEquals(device, service.authenticate(DEVICE_ID, "Bearer " + SECRET));
    }

    private static GateController device(String secretHash) {
        GateController device = new GateController();
        device.setControllerId(DEVICE_ID);
        device.setDeviceSecretHash(secretHash);
        return device;
    }
}
