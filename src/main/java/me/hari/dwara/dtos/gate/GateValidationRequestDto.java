package me.hari.dwara.dtos.gate;

import me.hari.dwara.entities.enums.GateIntent;

import java.util.UUID;

/**
 * Body of {@code POST /gate/validate}, exactly as the firmware sends it
 * ({@code firmware/dwara-gate/BackendClient.h}):
 * {@code { deviceId, sessionId, intent: "ENTRY" | "EXIT" }}.
 */
public record GateValidationRequestDto(
        UUID deviceId,
        UUID sessionId,
        GateIntent intent
) {
}
