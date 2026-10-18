package me.hari.dwara.services;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Spec D20: CREATED sessions the user never took to a gate would otherwise sit
 * active forever and block D9's one-session-per-user rule.
 *
 * <p>This is only the trigger — the work lives in
 * {@link ParkingSessionService#abortStaleSessions()} so it can be driven
 * directly and tested without waiting on a scheduler.
 */
@Component
@RequiredArgsConstructor
public class ScheduledSessionAborter {

    private static final Logger LOG = LoggerFactory.getLogger(ScheduledSessionAborter.class);

    private final ParkingSessionService sessionService;

    @Scheduled(fixedDelayString = "${dwara.sessions.stale-sweep-interval:PT1M}")
    public void sweepStaleSessions() {
        int aborted = sessionService.abortStaleSessions();
        if (aborted > 0) {
            LOG.info("Aborted {} stale parking session(s).", aborted);
        }
    }
}
