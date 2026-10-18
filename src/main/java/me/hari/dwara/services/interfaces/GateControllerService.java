package me.hari.dwara.services.interfaces;

import me.hari.dwara.dtos.ResponseObject;
import me.hari.dwara.dtos.gate.GateDecisionDto;

import java.util.UUID;

/**
 * Authorization authority for the gate (spec §3.3 entry, §3.5 exit, §10 edge
 * cases).
 *
 * <p>The ESP32 never acts on BLE content alone: it carries
 * {@code { deviceId, sessionId, intent }} to the backend and only moves the
 * servo on an {@code open} verdict. The device id is part of the contract
 * because the "device belongs to the session's facility" check (§3.3 step 3)
 * is what stops a gate at facility A opening a session started at facility B.
 *
 * <p>Refusals are business outcomes, not errors: they come back as a
 * successful {@link ResponseObject} carrying
 * {@link GateDecisionDto#decision()} = {@code keep_closed} plus a
 * {@link me.hari.dwara.entities.enums.DenyReason}, so the firmware can relay
 * the reason to the app. Only a genuinely unknown device is an error response.
 */
public interface GateControllerService {
    /**
     * Authorize an ENTRY for {@code sessionId} at gate {@code deviceId}.
     *
     * <p>On success the backend owns the billing clock (spec D3/D19): it stamps
     * {@code entryTimestamp} and moves the session {@code CREATED → PARKED}.
     */
    ResponseObject<GateDecisionDto> openEntryGate(UUID deviceId, UUID sessionId);

    /**
     * Authorize an EXIT for {@code sessionId} at gate {@code deviceId}.
     *
     * <p>Requires a {@code PAYMENT_PENDING} session backed by a successful
     * payment; on success the session becomes {@code EXITED} (spec §3.5).
     */
    ResponseObject<GateDecisionDto> openExitGate(UUID deviceId, UUID sessionId);
}
