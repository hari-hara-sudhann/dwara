package me.hari.dwara.dtos.parkingsession;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SessionDto(
        UUID sessionId,
        Instant entryAt,
        BigDecimal estimatedFee
) {}