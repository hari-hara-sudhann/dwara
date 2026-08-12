package me.hari.dwara.mappers;

import me.hari.dwara.dtos.parkingfacility.FacilityCreationRequestDto;
import me.hari.dwara.entities.ParkingFacility;
import me.hari.dwara.entities.enums.ParkingFacilityStatus;

public class FacilityMapper {
    public static ParkingFacility creationRequestToFacility(FacilityCreationRequestDto dto) {
        ParkingFacility facility = new ParkingFacility();
        facility.setFacilityCode(dto.facilityCode());
        facility.setName(dto.name());
        facility.setAddress(dto.address());
        facility.setStatus(ParkingFacilityStatus.INACTIVE);

        return facility;
    }
}
