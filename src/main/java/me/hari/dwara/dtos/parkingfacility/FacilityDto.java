package me.hari.dwara.dtos.parkingfacility;

import java.util.UUID;
import me.hari.dwara.entities.enums.ParkingFacilityStatus;

public record FacilityDto(
    UUID facilityId,
    String facilityCode,
    String name,
    String address,
    ParkingFacilityStatus status
) {}