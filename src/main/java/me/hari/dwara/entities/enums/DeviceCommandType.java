package me.hari.dwara.entities.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Remote command the backend queues for a gate (spec D11).
 *
 * <p>Wire values match the firmware's {@code RemoteCommand} parsing
 * ({@code firmware/dwara-gate/BackendClient.h}).
 *
 * <p>{@link #NONE} is response-only: it is what we hand back when the device
 * polls and nothing is queued, and it is never persisted as a queued command.
 */
public enum DeviceCommandType {
    OPEN("OPEN"),
    KEEP_CLOSED("KEEP_CLOSED"),
    NONE("NONE");

    private final String wireValue;

    DeviceCommandType(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String getWireValue() {
        return wireValue;
    }
}
