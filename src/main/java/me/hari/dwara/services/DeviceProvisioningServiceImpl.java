package me.hari.dwara.services;

import lombok.RequiredArgsConstructor;
import me.hari.dwara.dtos.ResponseObject;
import me.hari.dwara.dtos.gate.DeviceRegistrationRequestDto;
import me.hari.dwara.dtos.gate.DeviceRegistrationResultDto;
import me.hari.dwara.entities.GateController;
import me.hari.dwara.entities.ParkingFacility;
import me.hari.dwara.repositories.GateControllerRepository;
import me.hari.dwara.repositories.ParkingFacilityRepository;
import me.hari.dwara.services.interfaces.DeviceProvisioningService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Registers gate hardware (spec §5 {@code POST /devices}, spec D7).
 *
 * <p>The device secret is generated here and returned once. Storing only the
 * BCrypt hash keeps a database dump from handing out gate access — though, as
 * the spec notes, physical access to a flashed unit is still game over.
 */
@Service
@RequiredArgsConstructor
public class DeviceProvisioningServiceImpl implements DeviceProvisioningService {

    /** 256 bits, matching the entropy a bearer credential should have. */
    private static final int SECRET_BYTES = 32;

    private final GateControllerRepository gateControllerRepository;
    private final ParkingFacilityRepository facilityRepository;
    private final PasswordEncoder passwordEncoder;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public ResponseObject<DeviceRegistrationResultDto> registerDevice(DeviceRegistrationRequestDto dto) {
        ParkingFacility facility = facilityRepository.findByFacilityCode(dto.facilityCode())
                .orElse(null);
        if (facility == null) {
            return ResponseObject.failure("Parking facility does not exist.");
        }

        String deviceSecret = generateSecret();

        GateController device = new GateController();
        device.setFacility(facility);
        device.setGateType(dto.gateType());
        device.setFirmwareVersion(dto.firmwareVersion());
        device.setDeviceSecretHash(passwordEncoder.encode(deviceSecret));

        gateControllerRepository.save(device);

        return ResponseObject.success(
                "Gate controller registered successfully.",
                new DeviceRegistrationResultDto(device.getControllerId(), deviceSecret)
        );
    }

    private String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
