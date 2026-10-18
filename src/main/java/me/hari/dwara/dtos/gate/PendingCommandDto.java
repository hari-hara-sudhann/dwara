package me.hari.dwara.dtos.gate;

import me.hari.dwara.entities.enums.DeviceCommandType;

/**
 * Body of the {@code GET /devices/{deviceId}/commands} response (spec D11):
 * {@code { "command": "NONE" | "OPEN" | "KEEP_CLOSED" }}.
 */
public record PendingCommandDto(
        DeviceCommandType command
) {
}
