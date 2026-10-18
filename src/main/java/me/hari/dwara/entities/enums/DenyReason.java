package me.hari.dwara.entities.enums;

/**
 * Why the backend refused a gate action. The names mirror the {@code reason}
 * strings the firmware already understands
 * ({@code firmware/dwara-gate/GateTypes.h} → {@code DenyReason}); only the
 * reasons the backend itself can produce are listed here — the firmware-local
 * ones ({@code BACKEND_UNREACHABLE}, {@code ACTUATOR_FAULT},
 * {@code MALFORMED_COMMAND}) never come from the service.
 */
public enum DenyReason {
    NONE,
    STALE_SESSION,
    WRONG_STATE_FOR_ENTRY,
    PAYMENT_REQUIRED,
    WRONG_FACILITY,
    UNKNOWN_SESSION,
    BACKEND_ERROR
}
