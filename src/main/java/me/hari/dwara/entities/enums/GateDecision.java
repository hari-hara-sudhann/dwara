package me.hari.dwara.entities.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Verdict the backend hands back to the ESP32 for a gate request
 * (spec §3.3 step 4 / §3.5 step 4).
 *
 * <p>The wire values are fixed by the firmware, which compares the string
 * literally when it parses {@code POST /gate/validate}
 * (see {@code firmware/dwara-gate/BackendClient.h}).
 */
public enum GateDecision {
    OPEN("open"),
    KEEP_CLOSED("keep_closed");

    private final String wireValue;

    GateDecision(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String getWireValue() {
        return wireValue;
    }
}
