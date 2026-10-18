package me.hari.dwara.services.interfaces;

import me.hari.dwara.dtos.ResponseObject;
import me.hari.dwara.dtos.gate.DeviceRegistrationRequestDto;
import me.hari.dwara.dtos.gate.DeviceRegistrationResultDto;

/**
 * Registers gate hardware against a facility (spec §5 {@code POST /devices},
 * admin-key guarded, and spec D7 flash-time provisioning).
 */
public interface DeviceProvisioningService {
    /**
     * Register a gate controller and mint its device secret.
     *
     * <p>The plaintext secret is returned exactly once so it can be flashed
     * into the firmware; only its hash is persisted (spec D6/D11).
     */
    ResponseObject<DeviceRegistrationResultDto> registerDevice(DeviceRegistrationRequestDto dto);
}
