package me.hari.dwara.services;

import lombok.RequiredArgsConstructor;
import me.hari.dwara.dtos.ResponseObject;
import me.hari.dwara.dtos.parkingfacility.FacilityCreatedDto;
import me.hari.dwara.dtos.parkingfacility.FacilityCreationRequestDto;
import me.hari.dwara.entities.ParkingFacility;
import me.hari.dwara.mappers.FacilityMapper;
import me.hari.dwara.repositories.ParkingFacilityRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ParkingFacilityService {
    private final ParkingFacilityRepository facilityRepository;

    public ResponseObject<FacilityCreatedDto> createFacility(FacilityCreationRequestDto dto) {
        boolean facilityExists = facilityRepository.existsByFacilityCode(dto.facilityCode());

        if (facilityExists) {
            return ResponseObject.failure("Facility with code already exists.");
        }

        ParkingFacility facility = FacilityMapper.creationRequestToFacility(dto);

        facilityRepository.save(facility);

        return ResponseObject.success(
                "Parking facility successfully created",
                new FacilityCreatedDto(
                        facility.getFacilityId(),
                        facility.getStatus()
                )
        );
    }
}
