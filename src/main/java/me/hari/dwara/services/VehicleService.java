package me.hari.dwara.services;

import lombok.RequiredArgsConstructor;
import me.hari.dwara.dtos.ResponseObject;
import me.hari.dwara.dtos.vehicle.*;
import me.hari.dwara.entities.User;
import me.hari.dwara.entities.Vehicle;
import me.hari.dwara.mappers.VehicleMapper;
import me.hari.dwara.repositories.UserRepository;
import me.hari.dwara.repositories.VehicleRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VehicleService {
    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;

    public ResponseObject<VehicleCreatedDto> createVehicle(VehicleCreationRequestDto dto) {
        Optional<User> userContainer = userRepository.findById(dto.userId());

        if (userContainer.isEmpty()) {
            return ResponseObject.failure("User does not exist.");
        }

        User user = userContainer.get();

        Vehicle vehicle = VehicleMapper.creationRequestToVehicle(dto, user);

        vehicleRepository.save(vehicle);

        return ResponseObject.success(
                "Vehicle created successfully",
                VehicleMapper.toVehicleCreatedDto(vehicle)
        );
    }

    public ResponseObject<VehicleDetailsDto> findVehiclesByUser(UUID userId) {
        boolean userExists = userRepository.existsById(userId);
        if (!userExists) {
            return ResponseObject.failure("User does not exist.");
        }

        List<Vehicle> vehicles = vehicleRepository.findAllByUser_UserId(userId);

        VehicleDetailsDto responseDto = VehicleMapper.toVehicleDetailsDto(userId, vehicles);

        return new ResponseObject<>(
                true,
                "Found vehicles",
                responseDto
        );
    }

    public ResponseObject<Void> updateVehicleDetails(VehicleUpdationRequestDto dto) {
        Optional<User> userContainer = userRepository.findById(dto.userId());

        if (userContainer.isEmpty()) {
            return ResponseObject.failure("User does not exist");
        }

        User user = userContainer.get();
        Optional<Vehicle> vehicleContainer = vehicleRepository.findById(dto.vehicleId());
        if (vehicleContainer.isEmpty()) {
            return ResponseObject.failure("Vehicle does not exist.");
        }

        Vehicle vehicle = vehicleContainer.get();

        VehicleMapper.updationRequestToVehicle(vehicle,  dto);
        vehicleRepository.save(vehicle);

        return ResponseObject.success("Vehicle updated successfully.", null);
    }

    public ResponseObject<Void> deleteVehicle(VehicleDeletionRequestDto dto) {
        // Once JWT is implemented, remove userId() from dto and authenticate user using JWT.
        Optional<User> userContainer = userRepository.findById(dto.userId());

        if (userContainer.isEmpty()) {
            return ResponseObject.failure("User does not exist.");
        }

        User user = userContainer.get();

        Optional<Vehicle> vehicleContainer = vehicleRepository.findById(dto.vehicleId());

        if (vehicleContainer.isEmpty()) {
            return ResponseObject.failure("Vehicle does not exist.");
        }

        Vehicle vehicle = vehicleContainer.get();

        if (!vehicle.getUser().getUserId().equals(user.getUserId())) {
            return ResponseObject.failure( "User does not own the vehicle.");
        }

        vehicleRepository.delete(vehicle);
        return ResponseObject.success("Vehicle successfully deleted", null);
    }
}
