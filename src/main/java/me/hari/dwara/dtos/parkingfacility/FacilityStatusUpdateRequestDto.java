package me.hari.dwara.dtos.parkingfacility;

import me.hari.dwara.entities.enums.ParkingFacilityStatus;

/**
 * Body of {@code PUT /facilities/{facilityCode}/status} (spec §5):
 * {@code { "status": "ACTIVE" | "INACTIVE" | "MAINTENANCE" }}.
 */
public record FacilityStatusUpdateRequestDto(
        ParkingFacilityStatus status
) {
}
