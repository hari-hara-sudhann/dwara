package me.hari.dwara.controllers;

import lombok.RequiredArgsConstructor;
import me.hari.dwara.dtos.ResponseObject;
import me.hari.dwara.dtos.gate.GateDecisionDto;
import me.hari.dwara.dtos.gate.GateValidationRequestDto;
import me.hari.dwara.entities.enums.DenyReason;
import me.hari.dwara.entities.enums.GateDecision;
import me.hari.dwara.services.DeviceAuthenticationService;
import me.hari.dwara.services.InvalidDeviceCredentialsException;
import me.hari.dwara.services.interfaces.GateControllerService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The one authority the ESP32 consults before moving the gate (spec §3.3,
 * §3.5).
 *
 * <p>The response is deliberately the bare
 * {@code { "decision": "open" | "keep_closed", "reason": "..." }} object: the
 * firmware parses those fields at the top level
 * ({@code firmware/dwara-gate/BackendClient.h}), and the serial bridge
 * documents the same contract. App-facing endpoints keep the
 * {@code ResponseObject} envelope.
 */
@RestController
@RequestMapping("/gate")
@RequiredArgsConstructor
public class GateController {

    private final GateControllerService gateControllerService;
    private final DeviceAuthenticationService deviceAuthenticationService;

    @PostMapping("/validate")
    public GateDecisionDto validate(
            @RequestBody GateValidationRequestDto request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
            String authorization) {

        if (request.deviceId() == null) {
            throw new InvalidDeviceCredentialsException();
        }

        deviceAuthenticationService.authenticate(request.deviceId(), authorization);

        if (request.sessionId() == null || request.intent() == null) {
            return keepClosed(DenyReason.BACKEND_ERROR);
        }

        ResponseObject<GateDecisionDto> result = switch (request.intent()) {
            case ENTRY -> gateControllerService.openEntryGate(
                    request.deviceId(), request.sessionId());
            case EXIT -> gateControllerService.openExitGate(
                    request.deviceId(), request.sessionId());
        };

        return result.getSuccess()
                ? result.getResponse()
                : keepClosed(DenyReason.BACKEND_ERROR);
    }

    private static GateDecisionDto keepClosed(DenyReason reason) {
        return new GateDecisionDto(GateDecision.KEEP_CLOSED, reason);
    }
}
