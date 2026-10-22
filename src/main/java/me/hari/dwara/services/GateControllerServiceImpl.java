package me.hari.dwara.services;

import lombok.RequiredArgsConstructor;
import me.hari.dwara.configs.SessionProperties;
import me.hari.dwara.dtos.ResponseObject;
import me.hari.dwara.dtos.gate.GateDecisionDto;
import me.hari.dwara.entities.GateController;
import me.hari.dwara.entities.ParkingSession;
import me.hari.dwara.entities.Payment;
import me.hari.dwara.entities.enums.DenyReason;
import me.hari.dwara.entities.enums.GateDecision;
import me.hari.dwara.entities.enums.PaymentStatus;
import me.hari.dwara.entities.enums.SessionStatus;
import me.hari.dwara.repositories.GateControllerRepository;
import me.hari.dwara.repositories.ParkingSessionRepository;
import me.hari.dwara.repositories.PaymentRepository;
import me.hari.dwara.services.interfaces.GateControllerService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Gate authorization, per the architecture spec.
 *
 * <ul>
 *   <li>§3.3 — entry: device belongs to the session's facility, session is
 *       {@code CREATED}; backend stamps {@code entryTimestamp} and parks it.</li>
 *   <li>§3.5 — exit: session is {@code PAYMENT_PENDING} with a successful
 *       payment; it becomes {@code EXITED}.</li>
 *   <li>§10 — edge cases 1 (stale), 3 (entry while parked), 4 (exit unpaid),
 *       5 (grace re-open after exit).</li>
 * </ul>
 *
 * <p>The one thing it deliberately does <em>not</em> do is touch the servo:
 * gate mechanics stay in the firmware (spec D2), so this returns a verdict and
 * lets the ESP32 act on it.
 */
@Service
@RequiredArgsConstructor
public class GateControllerServiceImpl implements GateControllerService {

    private final GateControllerRepository gateControllerRepository;
    private final ParkingSessionRepository sessionRepository;
    private final PaymentRepository paymentRepository;

    /**
     * Grace window for re-opening an already-{@code EXITED} session (spec §10
     * #5). Defaults to the firmware's {@code GATE_OPEN_HOLD_MS} servo close
     * timer: once the servo has had time to come back down, a re-approach is a
     * real second exit attempt and is refused.
     */
    private final SessionProperties sessionProperties;

    @Override
    public ResponseObject<GateDecisionDto> openEntryGate(UUID deviceId, UUID sessionId) {
        GateController device = gateControllerRepository.findById(deviceId).orElse(null);
        if (device == null) {
            return ResponseObject.failure("Gate controller does not exist.");
        }

        ParkingSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            return keepClosed(DenyReason.UNKNOWN_SESSION);
        }

        if (!servesSession(device, session)) {
            return keepClosed(DenyReason.WRONG_FACILITY);
        }

        // Spec §10 #1: a session the stale-session job aborted can never open.
        if (session.getSessionStatus() == SessionStatus.ABORTED) {
            return keepClosed(DenyReason.STALE_SESSION);
        }

        // Spec §10 #3: ENTRY is only meaningful for a session that has not been
        // parked yet. PARKED / PAYMENT_PENDING / EXITED are all "already inside".
        if (session.getSessionStatus() != SessionStatus.CREATED) {
            return keepClosed(DenyReason.WRONG_STATE_FOR_ENTRY);
        }

        // Spec D3/D19: the backend clock is the billing clock, and it starts
        // when the backend authorizes the open — not when the servo moves.
        session.setEntryTimestamp(Instant.now());
        session.setSessionStatus(SessionStatus.PARKED);
        sessionRepository.save(session);

        return open();
    }

    @Override
    public ResponseObject<GateDecisionDto> openExitGate(UUID deviceId, UUID sessionId) {
        GateController device = gateControllerRepository.findById(deviceId).orElse(null);
        if (device == null) {
            return ResponseObject.failure("Gate controller does not exist.");
        }

        ParkingSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            return keepClosed(DenyReason.UNKNOWN_SESSION);
        }

        if (!servesSession(device, session)) {
            return keepClosed(DenyReason.WRONG_FACILITY);
        }

        switch (session.getSessionStatus()) {
            case ABORTED -> {
                return keepClosed(DenyReason.STALE_SESSION);
            }
            case EXITED -> {
                // Spec §10 #5: the fee is already settled, so a re-approach
                // within the close window is a grace re-open rather than fraud.
                return withinExitGraceWindow(session)
                        ? open()
                        : keepClosed(DenyReason.STALE_SESSION);
            }
            case PAYMENT_PENDING -> {
                if (!hasSuccessfulPayment(session)) {
                    return keepClosed(DenyReason.PAYMENT_REQUIRED);
                }
                // The exit timestamp is stamped here, not at payment: it is
                // when the car actually leaves, and it anchors the grace
                // window for a re-approach (spec §10 #5).
                Instant exitedAt = Instant.now();
                session.setSessionStatus(SessionStatus.EXITED);
                session.setExitTimestamp(exitedAt);
                session.setExitGateOpenedAt(exitedAt);
                sessionRepository.save(session);
                return open();
            }
            // CREATED / PARKED — the fee has not been settled yet (§10 #4).
            default -> {
                return keepClosed(DenyReason.PAYMENT_REQUIRED);
            }
        }
    }

    /** The device must sit at the same facility the session was started in. */
    private boolean servesSession(GateController device, ParkingSession session) {
        return device.getFacility() != null
                && session.getFacility() != null
                && device.getFacility().getFacilityId()
                        .equals(session.getFacility().getFacilityId());
    }

    private boolean hasSuccessfulPayment(ParkingSession session) {
        return paymentRepository.findBySession(session)
                .map(Payment::getStatus)
                .filter(status -> status == PaymentStatus.SUCCESS)
                .isPresent();
    }

    private boolean withinExitGraceWindow(ParkingSession session) {
        // Measured from the exit gate open — never from payment success, or a
        // user who pays and then dawdles would lose the grace re-open.
        Instant openedAt = session.getExitGateOpenedAt();
        return openedAt != null
                && Instant.now().isBefore(openedAt.plus(sessionProperties.exitGraceWindow()));
    }

    private static ResponseObject<GateDecisionDto> open() {
        return ResponseObject.success(
                "Gate may open.",
                new GateDecisionDto(GateDecision.OPEN, DenyReason.NONE)
        );
    }

    private static ResponseObject<GateDecisionDto> keepClosed(DenyReason reason) {
        return ResponseObject.success(
                "Gate stays closed.",
                new GateDecisionDto(GateDecision.KEEP_CLOSED, reason)
        );
    }
}
