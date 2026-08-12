package me.hari.dwara.dtos.parkingfacility;

import me.hari.dwara.entities.enums.ParkingFacilityStatus;

import java.util.UUID;

public record FacilityCreatedDto(
        UUID facilityId,
        ParkingFacilityStatus status
) {}
