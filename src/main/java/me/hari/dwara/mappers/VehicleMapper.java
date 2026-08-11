package me.hari.dwara.mappers;

import me.hari.dwara.dtos.vehicle.*;
import me.hari.dwara.entities.User;
import me.hari.dwara.entities.Vehicle;

import java.util.List;
import java.util.UUID;

public class VehicleMapper {
    public static VehicleCreatedDto toVehicleCreatedDto(Vehicle vehicle) {
        return new VehicleCreatedDto(vehicle.getVehicleId());
    }

    public static VehicleDetailsDto toVehicleDetailsDto(UUID userId, List<Vehicle> vehicles) {
        List<VehiclesDto> dto = vehicles.stream()
                .map(VehicleMapper::toVehiclesDto)
                .toList();

        return new VehicleDetailsDto(userId, dto);
    }

    private static VehiclesDto toVehiclesDto(Vehicle vehicle) {
        return new VehiclesDto(
                vehicle.getVehicleId(),
                vehicle.getNumberPlate(),
                vehicle.getVehicleType()
        );
    }

    public static Vehicle creationRequestToVehicle(VehicleCreationRequestDto dto, User user) {
        Vehicle vehicle = new Vehicle();
        vehicle.setUser(user);
        vehicle.setNumberPlate(dto.numberPlate());
        vehicle.setVehicleType(dto.type());
        return vehicle;
    }

    public static Vehicle updationRequestToVehicle(User user, VehicleUpdationRequestDto dto) {
        Vehicle vehicle = new Vehicle();
        vehicle.setUser(user);
        vehicle.setNumberPlate(dto.numberPlate());
        vehicle.setVehicleType(dto.vehicleType());
        return vehicle;
    }
}
