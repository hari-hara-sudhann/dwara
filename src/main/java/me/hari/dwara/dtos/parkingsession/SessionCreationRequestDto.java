package me.hari.dwara.dtos.parkingsession;

import java.util.UUID;

public record SessionCreationRequestDto(
    String facilityCode,
    UUID userId,
    UUID vehicleId
) {}
