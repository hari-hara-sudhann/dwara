package me.hari.dwara.dtos.parkingfacility;


import me.hari.dwara.entities.records.PricingPolicy;

public record FacilityCreationRequestDto(
        String name,
        String address,
        String facilityCode,
        PricingPolicy policy
) {}
