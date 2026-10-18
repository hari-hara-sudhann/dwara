package me.hari.dwara.entities.enums;

/**
 * Delivery state of a queued remote command (spec D11: the ESP32 polls, and the
 * poll itself is the acknowledgement).
 */
public enum CommandStatus {
    PENDING,
    DELIVERED
}
