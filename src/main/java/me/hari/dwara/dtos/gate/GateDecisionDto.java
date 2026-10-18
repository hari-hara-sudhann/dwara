package me.hari.dwara.dtos.gate;

import me.hari.dwara.entities.enums.DenyReason;
import me.hari.dwara.entities.enums.GateDecision;

/**
 * Body of the {@code POST /gate/validate} response (spec §3.3/§3.5):
 * {@code { "decision": "open" | "keep_closed", "reason": "..." }}.
 *
 * <p>{@code reason} is {@link DenyReason#NONE} whenever {@code decision} is
 * {@link GateDecision#OPEN}.
 */
public record GateDecisionDto(
        GateDecision decision,
        DenyReason reason
) {
}
