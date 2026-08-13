package me.hari.dwara.dtos.vehicle;

import java.util.List;
import java.util.UUID;

public record VehicleDetailsDto(
        UUID userId,
        List<VehiclesDto> vehicles
) {}
