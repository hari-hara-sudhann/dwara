package me.hari.dwara.dtos.vehicle;

import me.hari.dwara.entities.enums.VehicleType;

import java.util.UUID;

public record VehicleUpdationRequestDto(
        UUID userId,
        String numberPlate,
        VehicleType vehicleType
) { }
