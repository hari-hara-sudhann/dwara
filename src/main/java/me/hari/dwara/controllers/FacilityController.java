package me.hari.dwara.controllers;

import lombok.RequiredArgsConstructor;
import me.hari.dwara.dtos.ResponseObject;
import me.hari.dwara.dtos.parkingfacility.FacilityCreatedDto;
import me.hari.dwara.dtos.parkingfacility.FacilityCreationRequestDto;
import me.hari.dwara.dtos.parkingfacility.FacilityDto;
import me.hari.dwara.dtos.parkingfacility.FacilityStatusUpdateRequestDto;
import me.hari.dwara.entities.records.PricingPolicy;
import me.hari.dwara.services.ParkingFacilityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Facility lookup and administration (spec §5).
 *
 * <p>{@code POST} and both {@code PUT}s are admin-key endpoints; the key is
 * enforced by {@link me.hari.dwara.configs.AdminKeyFilter} before this
 * controller is reached. {@code GET /facilities/{facilityCode}} is the normal
 * JWT path the app uses after BLE discovery (D1).
 */
@RestController
@RequestMapping("/facilities")
@RequiredArgsConstructor
public class FacilityController {

    private final ParkingFacilityService facilityService;

    @PostMapping
    public ResponseObject<FacilityCreatedDto> create(
            @RequestBody FacilityCreationRequestDto dto) {

        if (dto.facilityCode() == null || dto.policy() == null) {
            return ResponseObject.failure("facilityCode and policy are required.");
        }

        return facilityService.createFacility(dto);
    }

    @GetMapping("/{facilityCode}")
    public ResponseObject<FacilityDto> get(@PathVariable String facilityCode) {
        return facilityService.findFacility(facilityCode);
    }

    @PutMapping("/{facilityCode}/pricing")
    public ResponseObject<Void> updatePricing(
            @PathVariable String facilityCode,
            @RequestBody PricingPolicy policy) {

        return facilityService.updatePricingPolicy(facilityCode, policy);
    }

    @PutMapping("/{facilityCode}/status")
    public ResponseObject<Void> updateStatus(
            @PathVariable String facilityCode,
            @RequestBody FacilityStatusUpdateRequestDto dto) {

        if (dto.status() == null) {
            return ResponseObject.failure("status is required.");
        }

        return facilityService.updateStatus(facilityCode, dto.status());
    }
}
