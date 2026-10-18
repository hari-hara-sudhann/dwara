package me.hari.dwara.configs;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Session timing knobs, bound from {@code dwara.sessions.*}.
 *
 * <p>Defaults come from the spec: D20 aborts stale {@code CREATED} sessions
 * after 15 minutes, and §10 #5's grace window mirrors the firmware's
 * {@code GATE_OPEN_HOLD_MS} servo close timer.
 */
@ConfigurationProperties(prefix = "dwara.sessions")
public record SessionProperties(
        Duration staleAfter,
        Duration exitGraceWindow
) {
    public SessionProperties {
        if (staleAfter == null) {
            staleAfter = Duration.ofMinutes(15);
        }
        if (exitGraceWindow == null) {
            exitGraceWindow = Duration.ofSeconds(8);
        }
    }
}
