package me.hari.dwara.services;

import lombok.RequiredArgsConstructor;
import me.hari.dwara.dtos.ResponseObject;
import me.hari.dwara.dtos.parkingfacility.FacilityCreatedDto;
import me.hari.dwara.dtos.parkingfacility.FacilityCreationRequestDto;
import me.hari.dwara.dtos.parkingfacility.FacilityDto;
import me.hari.dwara.dtos.parkingfacility.FacilityUpdateRequestDto;
import me.hari.dwara.entities.ParkingFacility;
import me.hari.dwara.entities.enums.ParkingFacilityStatus;
import me.hari.dwara.entities.records.PricingPolicy;
import me.hari.dwara.mappers.FacilityMapper;
import me.hari.dwara.repositories.ParkingFacilityRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

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

    public ResponseObject<FacilityDto> findFacility(String facilityCode) {
        Optional<ParkingFacility> facility = facilityRepository.findByFacilityCode(facilityCode);

        return facility.map(parkingFacility -> ResponseObject.success(
                "Facility found.",
                FacilityMapper.toDto(parkingFacility)
        )).orElseGet(() -> ResponseObject.failure("Facility does not exist."));

    }

    public ResponseObject<Void> updateFacilityDetails(FacilityUpdateRequestDto dto) {
        Optional<ParkingFacility> facilityContainer = facilityRepository.findByFacilityCode(dto.facilityCode());
        if (facilityContainer.isEmpty()) {
            return ResponseObject.failure("Parking Facility does not exist.");
        }

        ParkingFacility facility = facilityContainer.get();

        FacilityMapper.updationRequestToFacility(dto, facility);

        facilityRepository.save(facility);

        return ResponseObject.success("Parking facility updated successfully.", null);
    }

    public ResponseObject<Void> updateStatus(String facilityCode, ParkingFacilityStatus status) {
        Optional<ParkingFacility> facilityContainer = facilityRepository.findByFacilityCode(facilityCode);

        if (facilityContainer.isEmpty()) {
            return ResponseObject.failure("Parking facility does not exist.");
        }

        ParkingFacility facility = facilityContainer.get();
        facility.setStatus(status);
        facilityRepository.save(facility);

        return ResponseObject.success("Facility status updated successfully.", null);
    }

    public ResponseObject<Void> updatePricingPolicy(String facilityCode, PricingPolicy policy) {
        Optional<ParkingFacility> facilityContainer = facilityRepository.findByFacilityCode(facilityCode);

        if (facilityContainer.isEmpty()) {
            return ResponseObject.failure("Parking facility does not exist.");
        }

        ParkingFacility facility = facilityContainer.get();
        facility.setPricingPolicy(policy);
        facilityRepository.save(facility);

        return ResponseObject.success("Pricing policy updated successfully.", null);
    }

    public ResponseObject<Void> deleteFacility(String facilityCode) {
        boolean facilityExists = facilityRepository.existsByFacilityCode(facilityCode);

        if (!facilityExists) {
            return ResponseObject.failure("Parking facility does not exist.");
        }

        facilityRepository.deleteByFacilityCode(facilityCode);

        return ResponseObject.success("Parking facility deleted successfully.", null);
    }
}
