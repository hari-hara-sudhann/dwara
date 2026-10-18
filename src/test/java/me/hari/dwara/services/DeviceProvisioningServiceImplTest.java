package me.hari.dwara.services;

import me.hari.dwara.dtos.ResponseObject;
import me.hari.dwara.dtos.gate.DeviceRegistrationRequestDto;
import me.hari.dwara.dtos.gate.DeviceRegistrationResultDto;
import me.hari.dwara.entities.GateController;
import me.hari.dwara.entities.ParkingFacility;
import me.hari.dwara.entities.enums.GateType;
import me.hari.dwara.repositories.GateControllerRepository;
import me.hari.dwara.repositories.ParkingFacilityRepository;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceProvisioningServiceImplTest {

    private static final UUID DEVICE_ID = UUID.randomUUID();
    private static final String CODE = "FAC-0001";

    @Mock
    private GateControllerRepository gateControllerRepository;
    @Mock
    private ParkingFacilityRepository facilityRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private DeviceProvisioningServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DeviceProvisioningServiceImpl(
                gateControllerRepository, facilityRepository, passwordEncoder);
    }

    @Test
    void registerDevice_refusesAnUnknownFacility() {
        when(facilityRepository.findByFacilityCode(CODE)).thenReturn(Optional.empty());

        ResponseObject<DeviceRegistrationResultDto> result = service.registerDevice(request());

        assertFalse(result.getSuccess());
        assertEquals("Parking facility does not exist.", result.getMessage());
        verify(gateControllerRepository, never()).save(any());
    }

    @Test
    void registerDevice_mintsASecretAndStoresOnlyItsHash() {
        ParkingFacility facility = new ParkingFacility();
        facility.setFacilityId(UUID.randomUUID());
        when(facilityRepository.findByFacilityCode(CODE)).thenReturn(Optional.of(facility));
        when(passwordEncoder.encode(anyString())).thenReturn("BCRYPT-HASH");
        when(gateControllerRepository.save(any(GateController.class))).thenAnswer(invocation -> {
            GateController device = invocation.getArgument(0);
            device.setControllerId(DEVICE_ID);
            return device;
        });

        ResponseObject<DeviceRegistrationResultDto> result = service.registerDevice(request());

        assertTrue(result.getSuccess());
        assertEquals(DEVICE_ID, result.getResponse().controllerId());
        assertNotNull(result.getResponse().deviceSecret());
        assertFalse(result.getResponse().deviceSecret().isBlank());

        ArgumentCaptor<GateController> saved = ArgumentCaptor.forClass(GateController.class);
        verify(gateControllerRepository).save(saved.capture());
        assertEquals("BCRYPT-HASH", saved.getValue().getDeviceSecretHash());
        assertEquals(facility, saved.getValue().getFacility());
        assertEquals(GateType.ENTRY, saved.getValue().getGateType());
        assertEquals("1.0.0", saved.getValue().getFirmwareVersion());

        // The plaintext secret is hashed before it is ever handed back.
        verify(passwordEncoder).encode(result.getResponse().deviceSecret());
    }

    @Test
    void registerDevice_generatesADifferentSecretEveryTime() {
        ParkingFacility facility = new ParkingFacility();
        when(facilityRepository.findByFacilityCode(CODE)).thenReturn(Optional.of(facility));
        when(passwordEncoder.encode(anyString())).thenReturn("BCRYPT-HASH");

        String first = service.registerDevice(request()).getResponse().deviceSecret();
        String second = service.registerDevice(request()).getResponse().deviceSecret();

        assertNotEquals(first, second);
    }

    private static DeviceRegistrationRequestDto request() {
        return new DeviceRegistrationRequestDto(CODE, GateType.ENTRY, "1.0.0");
    }
}
