package me.hari.dwara.controllers;

import lombok.RequiredArgsConstructor;
import me.hari.dwara.dtos.ResponseObject;
import me.hari.dwara.dtos.vehicle.VehicleCreatedDto;
import me.hari.dwara.dtos.vehicle.VehicleCreationRequestDto;
import me.hari.dwara.dtos.vehicle.VehicleDeletionRequestDto;
import me.hari.dwara.dtos.vehicle.VehicleDetailsDto;
import me.hari.dwara.dtos.vehicle.VehicleUpdationRequestDto;
import me.hari.dwara.services.VehicleService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Vehicle CRUD for the authenticated user (spec §5). The owner is always taken
 * from the JWT, never from the request body, so a caller cannot add or mutate a
 * vehicle on someone else's account.
 */
@RestController
@RequestMapping("/vehicles")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService vehicleService;

    @PostMapping
    public ResponseObject<VehicleCreatedDto> create(
            @AuthenticationPrincipal UUID userId,
            @RequestBody VehicleCreationRequestDto dto) {

        if (dto.numberPlate() == null || dto.type() == null) {
            return ResponseObject.failure("numberPlate and type are required.");
        }

        return vehicleService.createVehicle(
                new VehicleCreationRequestDto(userId, dto.numberPlate(), dto.type()));
    }

    @GetMapping
    public ResponseObject<VehicleDetailsDto> list(@AuthenticationPrincipal UUID userId) {
        return vehicleService.findVehiclesByUser(userId);
    }

    @PatchMapping("/{vehicleId}")
    public ResponseObject<Void> update(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID vehicleId,
            @RequestBody VehicleUpdationRequestDto dto) {

        return vehicleService.updateVehicleDetails(new VehicleUpdationRequestDto(
                userId, vehicleId, dto.numberPlate(), dto.vehicleType()));
    }

    @DeleteMapping("/{vehicleId}")
    public ResponseObject<Void> delete(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID vehicleId) {

        return vehicleService.deleteVehicle(new VehicleDeletionRequestDto(userId, vehicleId));
    }
}
