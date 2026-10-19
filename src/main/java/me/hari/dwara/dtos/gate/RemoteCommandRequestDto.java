package me.hari.dwara.dtos.gate;

import me.hari.dwara.entities.enums.DeviceCommandType;

/**
 * Body of the admin-key guarded remote-command request. The wire values are
 * the firmware's {@code OPEN} / {@code KEEP_CLOSED}; {@code NONE} is
 * response-only and not queueable.
 */
public record RemoteCommandRequestDto(
        DeviceCommandType command
) {
}
