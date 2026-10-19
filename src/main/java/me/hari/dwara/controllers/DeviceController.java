package me.hari.dwara.controllers;

import lombok.RequiredArgsConstructor;
import me.hari.dwara.dtos.ResponseObject;
import me.hari.dwara.dtos.gate.DeviceRegistrationRequestDto;
import me.hari.dwara.dtos.gate.DeviceRegistrationResultDto;
import me.hari.dwara.dtos.gate.PendingCommandDto;
import me.hari.dwara.dtos.gate.RemoteCommandRequestDto;
import me.hari.dwara.entities.enums.DeviceCommandType;
import me.hari.dwara.services.DeviceAuthenticationService;
import me.hari.dwara.services.GateCommandService;
import me.hari.dwara.services.interfaces.DeviceProvisioningService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Gate-hardware endpoints (spec §5, D7, D11).
 *
 * <ul>
 *   <li>{@code POST /devices} — admin-key registration; the secret is returned
 *       once and only its hash is stored.</li>
 *   <li>{@code GET /devices/{id}/commands} — device-secret poll. Returns the
 *       bare {@code {command: ...}} object the firmware parses, not the app
 *       response envelope.</li>
 *   <li>{@code POST /devices/{id}/commands} — admin-key enqueue for the
 *       attendant/override and D16 recovery path (§3.6).</li>
 * </ul>
 */
@RestController
@RequestMapping("/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceProvisioningService provisioningService;
    private final DeviceAuthenticationService deviceAuthenticationService;
    private final GateCommandService gateCommandService;

    @PostMapping
    public ResponseObject<DeviceRegistrationResultDto> register(
            @RequestBody DeviceRegistrationRequestDto dto) {

        if (dto.facilityCode() == null || dto.gateType() == null) {
            return ResponseObject.failure("facilityCode and gateType are required.");
        }

        return provisioningService.registerDevice(dto);
    }

    @GetMapping("/{deviceId}/commands")
    public PendingCommandDto pollCommands(
            @PathVariable UUID deviceId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
            String authorization) {

        deviceAuthenticationService.authenticate(deviceId, authorization);

        ResponseObject<PendingCommandDto> result = gateCommandService.pollCommands(deviceId);

        // Authentication already proved the device exists, so this is only a
        // defensive fallback that still keeps the firmware's parser happy.
        return result.getSuccess()
                ? result.getResponse()
                : new PendingCommandDto(DeviceCommandType.NONE);
    }

    @PostMapping("/{deviceId}/commands")
    public ResponseObject<Void> queueCommand(
            @PathVariable UUID deviceId,
            @RequestBody RemoteCommandRequestDto dto) {

        if (dto.command() == null) {
            return ResponseObject.failure("command is required.");
        }

        return switch (dto.command()) {
            case OPEN -> gateCommandService.requestOpen(deviceId);
            case KEEP_CLOSED -> gateCommandService.requestKeepClosed(deviceId);
            case NONE -> ResponseObject.failure("NONE is not a queueable command.");
        };
    }
}
