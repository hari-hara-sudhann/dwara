package me.hari.dwara.dtos.gate;

import java.util.UUID;

/**
 * Registration response. {@code deviceSecret} is the only time the plaintext is
 * ever handed out — the database keeps a hash (spec D6/D11), and the value is
 * flashed into the firmware at build time (spec D7).
 */
public record DeviceRegistrationResultDto(
        UUID controllerId,
        String deviceSecret
) {
}
