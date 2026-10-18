package me.hari.dwara.services;

import me.hari.dwara.dtos.ResponseObject;
import me.hari.dwara.dtos.parkingfacility.FacilityCreatedDto;
import me.hari.dwara.dtos.parkingfacility.FacilityCreationRequestDto;
import me.hari.dwara.dtos.parkingfacility.FacilityDto;
import me.hari.dwara.dtos.parkingfacility.FacilityUpdateRequestDto;
import me.hari.dwara.entities.ParkingFacility;
import me.hari.dwara.entities.enums.ParkingFacilityStatus;
import me.hari.dwara.entities.records.PricingPolicy;
import me.hari.dwara.repositories.ParkingFacilityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParkingFacilityServiceTest {

    private static final String CODE = "FAC-0001";
    private static final UUID FACILITY_ID = UUID.randomUUID();

    @Mock
    private ParkingFacilityRepository facilityRepository;

    private ParkingFacilityService service;

    @BeforeEach
    void setUp() {
        service = new ParkingFacilityService(facilityRepository);
    }

    // ------------------------------------------------------- createFacility --

    @Test
    void createFacility_refusesADuplicateCode() {
        when(facilityRepository.existsByFacilityCode(CODE)).thenReturn(true);

        ResponseObject<FacilityCreatedDto> result = service.createFacility(creationRequest());

        assertFalse(result.getSuccess());
        assertEquals("Facility with code already exists.", result.getMessage());
        verify(facilityRepository, never()).save(any());
    }

    @Test
    void createFacility_createsItInactiveUntilAnOperatorActivatesIt() {
        when(facilityRepository.existsByFacilityCode(CODE)).thenReturn(false);
        when(facilityRepository.save(any(ParkingFacility.class))).thenAnswer(invocation -> {
            ParkingFacility facility = invocation.getArgument(0);
            facility.setFacilityId(FACILITY_ID);
            return facility;
        });

        ResponseObject<FacilityCreatedDto> result = service.createFacility(creationRequest());

        assertTrue(result.getSuccess());
        assertEquals(FACILITY_ID, result.getResponse().facilityId());
        assertEquals(ParkingFacilityStatus.INACTIVE, result.getResponse().status());

        ArgumentCaptor<ParkingFacility> saved = ArgumentCaptor.forClass(ParkingFacility.class);
        verify(facilityRepository).save(saved.capture());
        assertEquals(CODE, saved.getValue().getFacilityCode());
        assertEquals("Dwara Central", saved.getValue().getName());
        assertEquals("12 MG Road", saved.getValue().getAddress());
        assertEquals(policy(), saved.getValue().getPricingPolicy());
    }

    // --------------------------------------------------------- findFacility --

    @Test
    void findFacility_returnsItWhenPresent() {
        when(facilityRepository.findByFacilityCode(CODE)).thenReturn(Optional.of(facility()));

        ResponseObject<FacilityDto> result = service.findFacility(CODE);

        assertTrue(result.getSuccess());
        assertEquals(CODE, result.getResponse().facilityCode());
        assertEquals("Dwara Central", result.getResponse().name());
    }

    @Test
    void findFacility_reportsAMissingCode() {
        when(facilityRepository.findByFacilityCode(CODE)).thenReturn(Optional.empty());

        ResponseObject<FacilityDto> result = service.findFacility(CODE);

        assertFalse(result.getSuccess());
        assertEquals("Facility does not exist.", result.getMessage());
    }

    // -------------------------------------------------- updateFacilityDetails --

    @Test
    void updateFacilityDetails_refusesAnUnknownFacility() {
        when(facilityRepository.findByFacilityCode(CODE)).thenReturn(Optional.empty());

        ResponseObject<Void> result = service.updateFacilityDetails(
                new FacilityUpdateRequestDto(CODE, "New name", "New address"));

        assertFalse(result.getSuccess());
        verify(facilityRepository, never()).save(any());
    }

    @Test
    void updateFacilityDetails_appliesTheNewNameAndAddress() {
        ParkingFacility facility = facility();
        when(facilityRepository.findByFacilityCode(CODE)).thenReturn(Optional.of(facility));

        ResponseObject<Void> result = service.updateFacilityDetails(
                new FacilityUpdateRequestDto(CODE, "New name", "New address"));

        assertTrue(result.getSuccess());
        assertEquals("New name", facility.getName());
        assertEquals("New address", facility.getAddress());
        verify(facilityRepository).save(facility);
    }

    // ------------------------------------------------------------ updateStatus --

    @Test
    void updateStatus_refusesAnUnknownFacility() {
        when(facilityRepository.findByFacilityCode(CODE)).thenReturn(Optional.empty());

        ResponseObject<Void> result = service.updateStatus(CODE, ParkingFacilityStatus.MAINTENANCE);

        assertFalse(result.getSuccess());
        verify(facilityRepository, never()).save(any());
    }

    @Test
    void updateStatus_movesTheFacilityToTheNewState() {
        ParkingFacility facility = facility();
        when(facilityRepository.findByFacilityCode(CODE)).thenReturn(Optional.of(facility));

        ResponseObject<Void> result = service.updateStatus(CODE, ParkingFacilityStatus.MAINTENANCE);

        assertTrue(result.getSuccess());
        assertEquals(ParkingFacilityStatus.MAINTENANCE, facility.getStatus());
        verify(facilityRepository).save(facility);
    }

    // ----------------------------------------------------- updatePricingPolicy --

    @Test
    void updatePricingPolicy_refusesAnUnknownFacility() {
        when(facilityRepository.findByFacilityCode(CODE)).thenReturn(Optional.empty());

        ResponseObject<Void> result = service.updatePricingPolicy(CODE, policy());

        assertFalse(result.getSuccess());
        verify(facilityRepository, never()).save(any());
    }

    @Test
    void updatePricingPolicy_replacesTheRates() {
        ParkingFacility facility = facility();
        when(facilityRepository.findByFacilityCode(CODE)).thenReturn(Optional.of(facility));

        PricingPolicy updated = new PricingPolicy(
                new BigDecimal("0.005"), new BigDecimal("0.010"), new BigDecimal("0.020"));

        ResponseObject<Void> result = service.updatePricingPolicy(CODE, updated);

        assertTrue(result.getSuccess());
        assertSame(updated, facility.getPricingPolicy());
        verify(facilityRepository).save(facility);
    }

    // ------------------------------------------------------- deleteFacility --

    @Test
    void deleteFacility_refusesAnUnknownFacility() {
        when(facilityRepository.existsByFacilityCode(CODE)).thenReturn(false);

        ResponseObject<Void> result = service.deleteFacility(CODE);

        assertFalse(result.getSuccess());
        verify(facilityRepository, never()).deleteByFacilityCode(any());
    }

    @Test
    void deleteFacility_removesItWhenItExists() {
        when(facilityRepository.existsByFacilityCode(CODE)).thenReturn(true);

        ResponseObject<Void> result = service.deleteFacility(CODE);

        assertTrue(result.getSuccess());
        verify(facilityRepository).deleteByFacilityCode(CODE);
    }

    // -------------------------------------------------------------- helpers --

    private static FacilityCreationRequestDto creationRequest() {
        return new FacilityCreationRequestDto("Dwara Central", "12 MG Road", CODE, policy());
    }

    private static PricingPolicy policy() {
        return new PricingPolicy(
                new BigDecimal("0.005"), new BigDecimal("0.010"), new BigDecimal("0.020"));
    }

    private static ParkingFacility facility() {
        ParkingFacility facility = new ParkingFacility();
        facility.setFacilityId(UUID.randomUUID());
        facility.setFacilityCode(CODE);
        facility.setName("Dwara Central");
        facility.setAddress("12 MG Road");
        facility.setStatus(ParkingFacilityStatus.ACTIVE);
        facility.setPricingPolicy(policy());
        return facility;
    }
}
