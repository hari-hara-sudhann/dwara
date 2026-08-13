package me.hari.dwara.dtos.parkingfacility;

public record FacilityUpdateRequestDto(
        String facilityCode,
        String name,
        String address
) { }
