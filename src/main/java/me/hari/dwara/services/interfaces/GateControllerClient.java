package me.hari.dwara.services.interfaces;

import java.util.UUID;

/**
 * Outbound half of the remote-command channel (spec D11): the backend queues a
 * command, the ESP32 collects it on its next poll.
 *
 * <p>The device is always addressed by id — a gate is a specific unit at a
 * specific facility, so "open the gate" without one is not a meaningful
 * request. Queueing is deliberately fire-and-forget: v1 has no delivery
 * receipt and the poll itself is the acknowledgement.
 */
public interface GateControllerClient {
    /** Queue an {@code OPEN} (attendant override / fail-open recovery, D16). */
    boolean openGate(UUID deviceId);

    /** Queue a {@code KEEP_CLOSED} (hold the gate shut). */
    boolean keepClosed(UUID deviceId);

    /**
     * Whether the backend can currently reach the device.
     *
     * <p>Only registration is observable in v1: spec D11 explicitly excludes
     * heartbeats, so there is no liveness signal to consult.
     */
    boolean isAvailable(UUID deviceId);
}
