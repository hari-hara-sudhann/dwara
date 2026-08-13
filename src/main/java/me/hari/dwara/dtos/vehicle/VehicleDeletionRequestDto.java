package me.hari.dwara.dtos.vehicle;

import java.util.UUID;

public record VehicleDeletionRequestDto(
        UUID userId,
        UUID vehicleId
) {}
