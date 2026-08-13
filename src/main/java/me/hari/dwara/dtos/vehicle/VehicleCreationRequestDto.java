package me.hari.dwara.dtos.vehicle;

import me.hari.dwara.entities.enums.UserRole;
import me.hari.dwara.entities.enums.VehicleType;

import java.util.UUID;

public record VehicleCreationRequestDto(
   UUID userId,
   String numberPlate,
   VehicleType type
) {}
