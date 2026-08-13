package me.hari.dwara.mappers;

import me.hari.dwara.dtos.parkingfacility.FacilityCreationRequestDto;
import me.hari.dwara.dtos.parkingfacility.FacilityUpdateRequestDto;
import me.hari.dwara.entities.ParkingFacility;
import me.hari.dwara.entities.enums.ParkingFacilityStatus;
import me.hari.dwara.dtos.parkingfacility.FacilityDto;

public class FacilityMapper {
    public static ParkingFacility creationRequestToFacility(FacilityCreationRequestDto dto) {
        ParkingFacility facility = new ParkingFacility();
        facility.setFacilityCode(dto.facilityCode());
        facility.setName(dto.name());
        facility.setAddress(dto.address());
        facility.setStatus(ParkingFacilityStatus.INACTIVE);
        facility.setPricingPolicy(dto.policy());

        return facility;
    }

    public static FacilityDto toDto(ParkingFacility facility) {
        return new FacilityDto(
            facility.getFacilityId(),
            facility.getFacilityCode(),
            facility.getName(),
            facility.getAddress(),
            facility.getStatus()
        );
    }

    public static void updationRequestToFacility(FacilityUpdateRequestDto dto, ParkingFacility facility) {
        facility.setName(dto.name());
        facility.setAddress(dto.address());
    }
}
