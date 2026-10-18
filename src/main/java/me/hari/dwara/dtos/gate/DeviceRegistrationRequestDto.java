package me.hari.dwara.dtos.gate;

import me.hari.dwara.entities.enums.GateType;

/**
 * Body of the admin-key guarded {@code POST /devices} (spec §5). The secret is
 * not supplied by the caller — the backend mints it (spec D7).
 */
public record DeviceRegistrationRequestDto(
        String facilityCode,
        GateType gateType,
        String firmwareVersion
) {
}
