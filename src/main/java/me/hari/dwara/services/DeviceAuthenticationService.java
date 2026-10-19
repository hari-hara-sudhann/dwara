package me.hari.dwara.services;

import lombok.RequiredArgsConstructor;
import me.hari.dwara.entities.GateController;
import me.hari.dwara.repositories.GateControllerRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Authenticates the ESP32 on its two device endpoints (spec D6/D11):
 * {@code POST /gate/validate} and {@code GET /devices/{id}/commands}.
 *
 * <p>The device sends {@code Authorization: Bearer <deviceSecret>}. Only the
 * BCrypt hash of that secret is stored (spec §4), so verification is the same
 * one-way comparison used for user passwords — a database dump cannot be
 * replayed against a gate.
 */
@Service
@RequiredArgsConstructor
public class DeviceAuthenticationService {

    private static final String BEARER_PREFIX = "Bearer ";

    private final GateControllerRepository gateControllerRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * @throws InvalidDeviceCredentialsException when the device is unknown, has
     *         no provisioned secret, or the presented secret does not match.
     */
    public GateController authenticate(UUID deviceId, String authorizationHeader) {
        GateController device = gateControllerRepository.findById(deviceId)
                .orElseThrow(InvalidDeviceCredentialsException::new);

        String secret = extractBearerToken(authorizationHeader);

        if (secret == null
                || device.getDeviceSecretHash() == null
                || !passwordEncoder.matches(secret, device.getDeviceSecretHash())) {
            throw new InvalidDeviceCredentialsException();
        }

        return device;
    }

    private static String extractBearerToken(String header) {
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
