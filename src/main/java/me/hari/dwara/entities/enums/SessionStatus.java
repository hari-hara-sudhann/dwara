package me.hari.dwara.entities.enums;

import java.util.List;

public enum SessionStatus {
    CREATED,
    PARKED,
    PAYMENT_PENDING,
    EXITED,
    ABORTED;

    /**
     * Statuses that hold the single-session slot of spec D9. {@code EXITED}
     * and {@code ABORTED} are terminal and free the user and vehicle up.
     */
    public static final List<SessionStatus> ACTIVE = List.of(
            CREATED, PARKED, PAYMENT_PENDING);
}
