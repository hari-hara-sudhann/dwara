package me.hari.dwara.dtos.parkingsession;

import me.hari.dwara.entities.enums.SessionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Session read model used by {@code GET /sessions/active} and
 * {@code GET /sessions}. It carries enough facility/vehicle context for the
 * app to render a session without extra lookups, and both the live estimate
 * ({@code estimatedFee}) and the frozen charged amount ({@code totalFee}).
 */
public record SessionDto(
        UUID sessionId,
        SessionStatus sessionStatus,
        String facilityCode,
        String facilityName,
        UUID vehicleId,
        String numberPlate,
        Instant createdAt,
        Instant entryAt,
        Instant exitAt,
        BigDecimal estimatedFee,
        BigDecimal totalFee
) {
}
