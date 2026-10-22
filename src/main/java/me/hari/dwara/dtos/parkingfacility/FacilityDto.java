package me.hari.dwara.dtos.parkingfacility;

import java.util.UUID;
import me.hari.dwara.entities.enums.ParkingFacilityStatus;
import me.hari.dwara.entities.records.PricingPolicy;

public record FacilityDto(
    UUID facilityId,
    String facilityCode,
    String name,
    String address,
    ParkingFacilityStatus status,
    // Per-second INR rates per vehicle class, so the app can show the tariff
    // before a session exists.
    PricingPolicy pricingPolicy
) {}