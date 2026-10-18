package me.hari.dwara.services;

import me.hari.dwara.dtos.ResponseObject;
import me.hari.dwara.dtos.vehicle.VehicleCreatedDto;
import me.hari.dwara.dtos.vehicle.VehicleCreationRequestDto;
import me.hari.dwara.dtos.vehicle.VehicleDeletionRequestDto;
import me.hari.dwara.dtos.vehicle.VehicleDetailsDto;
import me.hari.dwara.dtos.vehicle.VehicleUpdationRequestDto;
import me.hari.dwara.entities.User;
import me.hari.dwara.entities.Vehicle;
import me.hari.dwara.entities.enums.VehicleType;
import me.hari.dwara.repositories.UserRepository;
import me.hari.dwara.repositories.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID OTHER_USER_ID = UUID.randomUUID();
    private static final UUID VEHICLE_ID = UUID.randomUUID();

    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private UserRepository userRepository;

    private VehicleService service;

    @BeforeEach
    void setUp() {
        service = new VehicleService(vehicleRepository, userRepository);
    }

    // ------------------------------------------------------- createVehicle --

    @Test
    void createVehicle_refusesAnUnknownOwner() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        ResponseObject<VehicleCreatedDto> result = service.createVehicle(creationRequest());

        assertFalse(result.getSuccess());
        assertEquals("User does not exist.", result.getMessage());
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void createVehicle_attachesTheVehicleToItsOwner() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(invocation -> {
            Vehicle vehicle = invocation.getArgument(0);
            vehicle.setVehicleId(VEHICLE_ID);
            return vehicle;
        });

        ResponseObject<VehicleCreatedDto> result = service.createVehicle(creationRequest());

        assertTrue(result.getSuccess());
        assertEquals(VEHICLE_ID, result.getResponse().uuid());

        ArgumentCaptor<Vehicle> saved = ArgumentCaptor.forClass(Vehicle.class);
        verify(vehicleRepository).save(saved.capture());
        assertEquals("KA-01-AB-1234", saved.getValue().getNumberPlate());
        assertEquals(VehicleType.LMV, saved.getValue().getVehicleType());
        assertEquals(USER_ID, saved.getValue().getUser().getUserId());
    }

    // --------------------------------------------------- findVehiclesByUser --

    @Test
    void findVehiclesByUser_refusesAnUnknownUser() {
        when(userRepository.existsById(USER_ID)).thenReturn(false);

        ResponseObject<VehicleDetailsDto> result = service.findVehiclesByUser(USER_ID);

        assertFalse(result.getSuccess());
        verify(vehicleRepository, never()).findAllByUser_UserId(any());
    }

    @Test
    void findVehiclesByUser_returnsEveryVehicleOfTheUser() {
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(vehicleRepository.findAllByUser_UserId(USER_ID))
                .thenReturn(List.of(vehicle(USER_ID), vehicle(USER_ID)));

        ResponseObject<VehicleDetailsDto> result = service.findVehiclesByUser(USER_ID);

        assertTrue(result.getSuccess());
        assertEquals(USER_ID, result.getResponse().userId());
        assertEquals(2, result.getResponse().vehicles().size());
        assertEquals("OLD-PLATE", result.getResponse().vehicles().getFirst().numberPlate());
        assertEquals(VehicleType.MC, result.getResponse().vehicles().getFirst().type());
        assertEquals(VEHICLE_ID, result.getResponse().vehicles().getFirst().vehicleId());
    }

    // -------------------------------------------------- updateVehicleDetails --

    @Test
    void updateVehicle_refusesAnUnknownUser() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        ResponseObject<Void> result = service.updateVehicleDetails(updationRequest(USER_ID, VEHICLE_ID));

        assertFalse(result.getSuccess());
        assertEquals("User does not exist", result.getMessage());
    }

    @Test
    void updateVehicle_refusesAnUnknownVehicle() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
        when(vehicleRepository.findById(VEHICLE_ID)).thenReturn(Optional.empty());

        ResponseObject<Void> result = service.updateVehicleDetails(updationRequest(USER_ID, VEHICLE_ID));

        assertFalse(result.getSuccess());
        assertEquals("Vehicle does not exist.", result.getMessage());
    }

    @Test
    void updateVehicle_refusesSomeoneElsesVehicle() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
        when(vehicleRepository.findById(VEHICLE_ID)).thenReturn(Optional.of(vehicle(OTHER_USER_ID)));

        ResponseObject<Void> result = service.updateVehicleDetails(updationRequest(USER_ID, VEHICLE_ID));

        assertFalse(result.getSuccess());
        assertEquals("User does not own the vehicle", result.getMessage());
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void updateVehicle_appliesTheNewDetails() {
        Vehicle vehicle = vehicle(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
        when(vehicleRepository.findById(VEHICLE_ID)).thenReturn(Optional.of(vehicle));

        ResponseObject<Void> result = service.updateVehicleDetails(updationRequest(USER_ID, VEHICLE_ID));

        assertTrue(result.getSuccess());
        assertEquals("KA-01-AB-1234", vehicle.getNumberPlate());
        assertEquals(VehicleType.LMV, vehicle.getVehicleType());
        verify(vehicleRepository).save(vehicle);
    }

    // ------------------------------------------------------ deleteVehicle --

    @Test
    void deleteVehicle_refusesAnUnknownUser() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        ResponseObject<Void> result = service.deleteVehicle(deletionRequest(USER_ID, VEHICLE_ID));

        assertFalse(result.getSuccess());
        verify(vehicleRepository, never()).delete(any());
    }

    @Test
    void deleteVehicle_refusesAnUnknownVehicle() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
        when(vehicleRepository.findById(VEHICLE_ID)).thenReturn(Optional.empty());

        ResponseObject<Void> result = service.deleteVehicle(deletionRequest(USER_ID, VEHICLE_ID));

        assertFalse(result.getSuccess());
        assertEquals("Vehicle does not exist.", result.getMessage());
    }

    @Test
    void deleteVehicle_refusesSomeoneElsesVehicle() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
        when(vehicleRepository.findById(VEHICLE_ID)).thenReturn(Optional.of(vehicle(OTHER_USER_ID)));

        ResponseObject<Void> result = service.deleteVehicle(deletionRequest(USER_ID, VEHICLE_ID));

        assertFalse(result.getSuccess());
        assertEquals("User does not own the vehicle.", result.getMessage());
        verify(vehicleRepository, never()).delete(any());
    }

    @Test
    void deleteVehicle_removesTheOwnersVehicle() {
        Vehicle vehicle = vehicle(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
        when(vehicleRepository.findById(VEHICLE_ID)).thenReturn(Optional.of(vehicle));

        ResponseObject<Void> result = service.deleteVehicle(deletionRequest(USER_ID, VEHICLE_ID));

        assertTrue(result.getSuccess());
        verify(vehicleRepository).delete(vehicle);
    }

    // ------------------------------------------------------------- helpers --

    private static VehicleCreationRequestDto creationRequest() {
        return new VehicleCreationRequestDto(USER_ID, "KA-01-AB-1234", VehicleType.LMV);
    }

    private static VehicleUpdationRequestDto updationRequest(UUID userId, UUID vehicleId) {
        return new VehicleUpdationRequestDto(userId, vehicleId, "KA-01-AB-1234", VehicleType.LMV);
    }

    private static VehicleDeletionRequestDto deletionRequest(UUID userId, UUID vehicleId) {
        return new VehicleDeletionRequestDto(userId, vehicleId);
    }

    private static User user(UUID userId) {
        User user = new User();
        user.setUserId(userId);
        return user;
    }

    private static Vehicle vehicle(UUID ownerId) {
        Vehicle vehicle = new Vehicle();
        vehicle.setVehicleId(VEHICLE_ID);
        vehicle.setNumberPlate("OLD-PLATE");
        vehicle.setVehicleType(VehicleType.MC);
        vehicle.setUser(user(ownerId));
        return vehicle;
    }
}
