package me.hari.dwara.services;

import me.hari.dwara.configs.SessionProperties;
import me.hari.dwara.dtos.parkingsession.SessionDto;
import me.hari.dwara.entities.ParkingFacility;
import me.hari.dwara.entities.ParkingSession;
import me.hari.dwara.entities.Payment;
import me.hari.dwara.entities.User;
import me.hari.dwara.entities.Vehicle;
import me.hari.dwara.entities.enums.ParkingFacilityStatus;
import me.hari.dwara.entities.enums.PaymentStatus;
import me.hari.dwara.entities.enums.SessionStatus;
import me.hari.dwara.mappers.SessionMapper;
import me.hari.dwara.repositories.ParkingFacilityRepository;
import me.hari.dwara.repositories.UserRepository;
import me.hari.dwara.repositories.VehicleRepository;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import me.hari.dwara.dtos.ResponseObject;
import me.hari.dwara.dtos.parkingsession.SessionCreationRequestDto;
import me.hari.dwara.dtos.payment.PaymentOutcome;
import me.hari.dwara.dtos.payment.PaymentReceiptDto;
import me.hari.dwara.repositories.ParkingSessionRepository;
import me.hari.dwara.repositories.PaymentRepository;
import me.hari.dwara.services.interfaces.PaymentService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The parking session lifecycle (spec §3.2 creation, §3.4 accrual, §3.5 exit
 * payment, D9 anti-abuse, D20 stale abort).
 */
@Service
@RequiredArgsConstructor
public class ParkingSessionService {

    /**
     * Spec D9 / §10 #2: statuses that hold the single-session slot. {@code EXITED}
     * and {@code ABORTED} are terminal, so they free the user and the vehicle up.
     */
    private static final List<SessionStatus> ACTIVE_STATUSES = List.of(
            SessionStatus.CREATED,
            SessionStatus.PARKED,
            SessionStatus.PAYMENT_PENDING
    );

    private final ParkingSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final ParkingFacilityRepository facilityRepository;
    private final VehicleRepository vehicleRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final SessionProperties sessionProperties;

    public ResponseObject<UUID> createSession(SessionCreationRequestDto dto) {
        User user = userRepository.findById(dto.userId()).orElse(null);
        if (user == null)
            return ResponseObject.failure("User does not exist.");

        ParkingFacility facility = facilityRepository.findByFacilityCode(dto.facilityCode())
                .orElse(null);
        if (facility == null)
            return ResponseObject.failure("Parking facility does not exist.");

        // Spec §10 #9: a facility under maintenance stops taking new sessions,
        // but the ones already running are left alone.
        if (facility.getStatus() != ParkingFacilityStatus.ACTIVE)
            return ResponseObject.failure("Parking facility is not active.");

        Vehicle vehicle = vehicleRepository.findById(dto.vehicleId())
                .orElse(null);
        if (vehicle == null)
            return ResponseObject.failure("Vehicle does not exist.");

        if (!(vehicle.getUser().getUserId().equals(user.getUserId())))
            return ResponseObject.failure("Vehicle does not belong to the user.");

        // Spec D9: strict anti-abuse — one active session per user AND per
        // vehicle, so a user with two cars still cannot hold two sessions.
        if (sessionRepository.existsByUser_UserIdAndSessionStatusIn(user.getUserId(), ACTIVE_STATUSES))
            return ResponseObject.failure("User already has an active parking session.");

        if (sessionRepository.existsByVehicle_VehicleIdAndSessionStatusIn(vehicle.getVehicleId(), ACTIVE_STATUSES))
            return ResponseObject.failure("Vehicle already has an active parking session.");

        ParkingSession session = SessionMapper.creationRequestToSession(dto, user, facility, vehicle);
        sessionRepository.save(session);

        return ResponseObject.success("Session successfully created", session.getSessionId());
    }

    public ResponseObject<Void> checkIn(UUID sessionId) {
        ParkingSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null)
            return ResponseObject.failure("Parking session does not exist.");

        session.setEntryTimestamp(Instant.now());
        session.setSessionStatus(SessionStatus.PARKED);
        sessionRepository.save(session);
        return ResponseObject.success("Parked successfully.", null);
    }

    /**
     * Spec §5 {@code POST /sessions/{id}/cancel}: the user changes their mind
     * before reaching the gate. Only a {@code CREATED} session can be cancelled —
     * once the gate has authorized an entry the fee clock is running.
     */
    public ResponseObject<Void> cancel(UUID sessionId) {
        ParkingSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null)
            return ResponseObject.failure("Parking session does not exist.");

        if (session.getSessionStatus() != SessionStatus.CREATED)
            return ResponseObject.failure("Only a created session can be cancelled.");

        session.setSessionStatus(SessionStatus.ABORTED);
        sessionRepository.save(session);
        return ResponseObject.success("Session cancelled.", null);
    }

    /**
     * Spec §3.5 / D10: settle the fee and hand the session over to the exit gate.
     *
     * <p>The fee is frozen here (D-answer "fee freeze at payment success") and the
     * session moves to {@code PAYMENT_PENDING}, which is the only state the exit
     * gate will open for.
     */
    public ResponseObject<PaymentReceiptDto> pay(UUID sessionId) {
        ParkingSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null)
            return ResponseObject.failure("Parking session does not exist.");

        // CREATED means the car never went in (nothing to bill); PAYMENT_PENDING
        // and EXITED mean it is already settled.
        if (session.getSessionStatus() != SessionStatus.PARKED)
            return ResponseObject.failure("Only a parked session can be paid for.");

        BigDecimal fee = calculateFinalFee(session);

        PaymentOutcome outcome = paymentService.charge(session.getSessionId(), fee);
        if (!outcome.successful())
            return ResponseObject.failure("Payment was not successful.");

        // Spec D19: backend clock only. Nothing the phone or the ESP32 says
        // about time is allowed to move the fee.
        Instant paidAt = Instant.now();

        session.setTotalFee(fee);
        session.setExitTimestamp(paidAt);
        session.setSessionStatus(SessionStatus.PAYMENT_PENDING);
        sessionRepository.save(session);

        Payment payment = new Payment();
        payment.setSession(session);
        payment.setAmount(fee);
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setTransactionReference(outcome.transactionReference());
        paymentRepository.save(payment);

        return ResponseObject.success(
                "Payment successful.",
                new PaymentReceiptDto(
                        payment.getPaymentId(),
                        session.getSessionId(),
                        fee,
                        outcome.transactionReference(),
                        paidAt
                )
        );
    }

    /**
     * Spec D20: abort {@code CREATED} sessions that were never validated at a
     * gate. Returns how many were aborted, so the scheduler can log it.
     */
    public int abortStaleSessions() {
        Instant cutoff = Instant.now().minus(sessionProperties.staleAfter());

        List<ParkingSession> stale = sessionRepository
                .findBySessionStatusAndCreatedAtBefore(SessionStatus.CREATED, cutoff);

        stale.forEach(session -> session.setSessionStatus(SessionStatus.ABORTED));
        sessionRepository.saveAll(stale);

        return stale.size();
    }

    public ResponseObject<SessionDto> getActiveSession(UUID userId) {
        ParkingSession session = sessionRepository.findActiveSessionByUserId(userId).orElse(null);
        if (session == null)
            return ResponseObject.failure("No active session found");

        SessionDto dto = SessionMapper.toDto(session);

        return ResponseObject.success("Active session found.", dto);
    }

    public ResponseObject<BigDecimal> getParkingFee(UUID sessionId) {
        ParkingSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null)
            return ResponseObject.failure("Session does not exist.");

        Duration duration = Duration.between(session.getEntryTimestamp(), Instant.now());

        BigDecimal seconds = BigDecimal.valueOf(duration.getSeconds());

        BigDecimal rate = session.getFacility()
                .getPricingPolicy()
                .getRate(session.getVehicle()
                        .getVehicleType());

        BigDecimal fee = rate.multiply(seconds);
        return ResponseObject.success("Current fee", fee);
    }

    /**
     * Authoritative fee (spec D14): flat per-second, rounded <em>up</em> to the
     * paisa. Distinct from {@link #getParkingFee}, which is the cosmetic live
     * estimate the app polls.
     */
    private BigDecimal calculateFinalFee(ParkingSession session) {
        Duration parked = Duration.between(session.getEntryTimestamp(), Instant.now());

        BigDecimal rate = session.getFacility()
                .getPricingPolicy()
                .getRate(session.getVehicle().getVehicleType());

        return rate.multiply(BigDecimal.valueOf(parked.getSeconds()))
                .setScale(2, RoundingMode.CEILING);
    }
}
