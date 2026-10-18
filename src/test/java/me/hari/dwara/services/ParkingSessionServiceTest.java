package me.hari.dwara.services;

import me.hari.dwara.configs.SessionProperties;
import me.hari.dwara.dtos.ResponseObject;
import me.hari.dwara.dtos.parkingsession.SessionCreationRequestDto;
import me.hari.dwara.dtos.parkingsession.SessionDto;
import me.hari.dwara.dtos.payment.PaymentOutcome;
import me.hari.dwara.dtos.payment.PaymentReceiptDto;
import me.hari.dwara.entities.ParkingFacility;
import me.hari.dwara.entities.ParkingSession;
import me.hari.dwara.entities.Payment;
import me.hari.dwara.entities.User;
import me.hari.dwara.entities.Vehicle;
import me.hari.dwara.entities.enums.ParkingFacilityStatus;
import me.hari.dwara.entities.enums.PaymentStatus;
import me.hari.dwara.entities.enums.SessionStatus;
import me.hari.dwara.entities.enums.VehicleType;
import me.hari.dwara.entities.records.PricingPolicy;
import me.hari.dwara.repositories.ParkingFacilityRepository;
import me.hari.dwara.repositories.ParkingSessionRepository;
import me.hari.dwara.repositories.PaymentRepository;
import me.hari.dwara.repositories.UserRepository;
import me.hari.dwara.repositories.VehicleRepository;
import me.hari.dwara.services.interfaces.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParkingSessionServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID OTHER_USER_ID = UUID.randomUUID();
    private static final UUID VEHICLE_ID = UUID.randomUUID();
    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final String CODE = "FAC-0001";
    private static final ParkingFacilityStatus ACTIVE = ParkingFacilityStatus.ACTIVE;

    /** ₹0.01 per second on an LMV. */
    private static final PricingPolicy POLICY = new PricingPolicy(
            new BigDecimal("0.005"), new BigDecimal("0.010"), new BigDecimal("0.020"));

    @Mock
    private ParkingSessionRepository sessionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ParkingFacilityRepository facilityRepository;
    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentService paymentService;

    private ParkingSessionService service;

    @BeforeEach
    void setUp() {
        service = new ParkingSessionService(
                sessionRepository,
                userRepository,
                facilityRepository,
                vehicleRepository,
                paymentRepository,
                paymentService,
                new SessionProperties(Duration.ofMinutes(15), Duration.ofSeconds(8))
        );
    }

    // --------------------------------------------------------- createSession --

    @Test
    void createSession_refusesAnUnknownUser() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        ResponseObject<UUID> result = service.createSession(creationRequest());

        assertFalse(result.getSuccess());
        assertEquals("User does not exist.", result.getMessage());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void createSession_refusesAnUnknownFacility() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
        when(facilityRepository.findByFacilityCode(CODE)).thenReturn(Optional.empty());

        ResponseObject<UUID> result = service.createSession(creationRequest());

        assertFalse(result.getSuccess());
        assertEquals("Parking facility does not exist.", result.getMessage());
    }

    @Test
    void createSession_refusesAFacilityThatIsNotActive() {
        ParkingFacility facility = facility(ParkingFacilityStatus.MAINTENANCE);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
        when(facilityRepository.findByFacilityCode(CODE)).thenReturn(Optional.of(facility));

        ResponseObject<UUID> result = service.createSession(creationRequest());

        assertFalse(result.getSuccess());
        assertEquals("Parking facility is not active.", result.getMessage());
        verify(vehicleRepository, never()).findById(any());
    }

    @Test
    void createSession_refusesAnUnknownVehicle() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
        when(facilityRepository.findByFacilityCode(CODE)).thenReturn(Optional.of(facility(ACTIVE)));
        when(vehicleRepository.findById(VEHICLE_ID)).thenReturn(Optional.empty());

        ResponseObject<UUID> result = service.createSession(creationRequest());

        assertFalse(result.getSuccess());
        assertEquals("Vehicle does not exist.", result.getMessage());
    }

    @Test
    void createSession_refusesSomeoneElsesVehicle() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
        when(facilityRepository.findByFacilityCode(CODE)).thenReturn(Optional.of(facility(ACTIVE)));
        when(vehicleRepository.findById(VEHICLE_ID)).thenReturn(Optional.of(vehicle(OTHER_USER_ID)));

        ResponseObject<UUID> result = service.createSession(creationRequest());

        assertFalse(result.getSuccess());
        assertEquals("Vehicle does not belong to the user.", result.getMessage());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void createSession_refusesAUserWhoIsAlreadyParked() {
        stubResolvableParties();
        when(sessionRepository.existsByUser_UserIdAndSessionStatusIn(eq(USER_ID), any()))
                .thenReturn(true);

        ResponseObject<UUID> result = service.createSession(creationRequest());

        assertFalse(result.getSuccess());
        assertEquals("User already has an active parking session.", result.getMessage());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void createSession_refusesAVehicleThatIsAlreadyParked() {
        stubResolvableParties();
        when(sessionRepository.existsByUser_UserIdAndSessionStatusIn(eq(USER_ID), any()))
                .thenReturn(false);
        when(sessionRepository.existsByVehicle_VehicleIdAndSessionStatusIn(eq(VEHICLE_ID), any()))
                .thenReturn(true);

        ResponseObject<UUID> result = service.createSession(creationRequest());

        assertFalse(result.getSuccess());
        assertEquals("Vehicle already has an active parking session.", result.getMessage());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void createSession_storesItAsCreated() {
        stubResolvableParties();
        when(sessionRepository.existsByUser_UserIdAndSessionStatusIn(eq(USER_ID), any()))
                .thenReturn(false);
        when(sessionRepository.existsByVehicle_VehicleIdAndSessionStatusIn(eq(VEHICLE_ID), any()))
                .thenReturn(false);
        when(sessionRepository.save(any(ParkingSession.class))).thenAnswer(invocation -> {
            ParkingSession session = invocation.getArgument(0);
            session.setSessionId(SESSION_ID);
            return session;
        });

        ResponseObject<UUID> result = service.createSession(creationRequest());

        assertTrue(result.getSuccess());
        assertEquals(SESSION_ID, result.getResponse());

        ArgumentCaptor<ParkingSession> saved = ArgumentCaptor.forClass(ParkingSession.class);
        verify(sessionRepository).save(saved.capture());
        assertEquals(SessionStatus.CREATED, saved.getValue().getSessionStatus());
        // Spec D19: the billing clock does not start until the gate authorizes.
        assertNull(saved.getValue().getEntryTimestamp());
    }

    // ------------------------------------------------------------- checkIn --

    @Test
    void checkIn_refusesAnUnknownSession() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.empty());

        assertFalse(service.checkIn(SESSION_ID).getSuccess());
    }

    @Test
    void checkIn_stampsTheEntryTimeAndParksTheSession() {
        ParkingSession session = session(SessionStatus.CREATED);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        assertTrue(service.checkIn(SESSION_ID).getSuccess());
        assertNotNull(session.getEntryTimestamp());
        assertEquals(SessionStatus.PARKED, session.getSessionStatus());
        verify(sessionRepository).save(session);
    }

    // -------------------------------------------------------------- cancel --

    @Test
    void cancel_refusesAnUnknownSession() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.empty());

        assertFalse(service.cancel(SESSION_ID).getSuccess());
    }

    @Test
    void cancel_refusesASessionThatAlreadyStarted() {
        ParkingSession session = session(SessionStatus.PARKED);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        ResponseObject<Void> result = service.cancel(SESSION_ID);

        assertFalse(result.getSuccess());
        assertEquals("Only a created session can be cancelled.", result.getMessage());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void cancel_abortsACreatedSession() {
        ParkingSession session = session(SessionStatus.CREATED);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        ResponseObject<Void> result = service.cancel(SESSION_ID);

        assertTrue(result.getSuccess());
        assertEquals(SessionStatus.ABORTED, session.getSessionStatus());
        verify(sessionRepository).save(session);
    }

    // ----------------------------------------------------------------- pay --

    @Test
    void pay_refusesAnUnknownSession() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.empty());

        assertFalse(service.pay(SESSION_ID).getSuccess());
        verify(paymentService, never()).charge(any(), any());
    }

    @Test
    void pay_refusesASessionThatWasNeverParked() {
        ParkingSession session = session(SessionStatus.CREATED);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        ResponseObject<PaymentReceiptDto> result = service.pay(SESSION_ID);

        assertFalse(result.getSuccess());
        assertEquals("Only a parked session can be paid for.", result.getMessage());
        verify(paymentService, never()).charge(any(), any());
    }

    @Test
    void pay_refusesWhenTheGatewayDeclines() {
        ParkingSession session = parkedSession();
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(paymentService.charge(any(), any())).thenReturn(new PaymentOutcome(false, null));

        ResponseObject<PaymentReceiptDto> result = service.pay(SESSION_ID);

        assertFalse(result.getSuccess());
        assertEquals("Payment was not successful.", result.getMessage());
        verify(sessionRepository, never()).save(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void pay_freezesTheRoundedFeeAndMovesTheSessionToPaymentPending() {
        ParkingSession session = parkedSession();
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(paymentService.charge(eq(SESSION_ID), any()))
                .thenReturn(new PaymentOutcome(true, "MOCK-ref-1"));

        ResponseObject<PaymentReceiptDto> result = service.pay(SESSION_ID);

        assertTrue(result.getSuccess());

        // ₹0.010/s for 120 s, rounded up to the paisa (spec D14).
        BigDecimal expected = new BigDecimal("1.20");
        assertEquals(0, expected.compareTo(result.getResponse().amount()));
        assertEquals("MOCK-ref-1", result.getResponse().transactionReference());
        assertEquals(SESSION_ID, result.getResponse().sessionId());
        assertNotNull(result.getResponse().paidAt());

        assertEquals(0, expected.compareTo(session.getTotalFee()));
        assertEquals(SessionStatus.PAYMENT_PENDING, session.getSessionStatus());
        assertNotNull(session.getExitTimestamp(), "fee stops accruing at payment (D19)");
        verify(sessionRepository).save(session);

        ArgumentCaptor<Payment> savedPayment = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(savedPayment.capture());
        assertEquals(PaymentStatus.SUCCESS, savedPayment.getValue().getStatus());
        assertEquals("MOCK-ref-1", savedPayment.getValue().getTransactionReference());
        assertEquals(0, expected.compareTo(savedPayment.getValue().getAmount()));
        assertEquals(session, savedPayment.getValue().getSession());
    }

    // --------------------------------------------------- abortStaleSessions --

    @Test
    void abortStaleSessions_doesNothingWhenNothingIsStale() {
        when(sessionRepository.findBySessionStatusAndCreatedAtBefore(eq(SessionStatus.CREATED), any()))
                .thenReturn(List.of());

        assertEquals(0, service.abortStaleSessions());
        verify(sessionRepository).saveAll(List.of());
    }

    @Test
    void abortStaleSessions_abortsEveryStaleCreatedSession() {
        ParkingSession first = session(SessionStatus.CREATED);
        ParkingSession second = session(SessionStatus.CREATED);
        when(sessionRepository.findBySessionStatusAndCreatedAtBefore(eq(SessionStatus.CREATED), any()))
                .thenReturn(List.of(first, second));

        assertEquals(2, service.abortStaleSessions());
        assertEquals(SessionStatus.ABORTED, first.getSessionStatus());
        assertEquals(SessionStatus.ABORTED, second.getSessionStatus());
        verify(sessionRepository).saveAll(List.of(first, second));
    }

    // ---------------------------------------------------- getActiveSession --

    @Test
    void getActiveSession_reportsThatThereIsNone() {
        when(sessionRepository.findActiveSessionByUserId(USER_ID)).thenReturn(Optional.empty());

        assertFalse(service.getActiveSession(USER_ID).getSuccess());
    }

    @Test
    void getActiveSession_returnsTheLiveEstimate() {
        when(sessionRepository.findActiveSessionByUserId(USER_ID))
                .thenReturn(Optional.of(parkedSession()));

        ResponseObject<SessionDto> result = service.getActiveSession(USER_ID);

        assertTrue(result.getSuccess());
        assertEquals(SESSION_ID, result.getResponse().sessionId());
        assertNotNull(result.getResponse().entryAt());
    }

    // -------------------------------------------------------- getParkingFee --

    @Test
    void getParkingFee_refusesAnUnknownSession() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.empty());

        assertFalse(service.getParkingFee(SESSION_ID).getSuccess());
    }

    @Test
    void getParkingFee_accruesTheUnroundedPerSecondRate() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(parkedSession()));

        ResponseObject<BigDecimal> result = service.getParkingFee(SESSION_ID);

        assertTrue(result.getSuccess());
        assertEquals(0, new BigDecimal("1.20").compareTo(result.getResponse()));
    }

    // -------------------------------------------------------------- helpers --

    private void stubResolvableParties() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
        when(facilityRepository.findByFacilityCode(CODE)).thenReturn(Optional.of(facility(ACTIVE)));
        when(vehicleRepository.findById(VEHICLE_ID)).thenReturn(Optional.of(vehicle(USER_ID)));
    }

    private static SessionCreationRequestDto creationRequest() {
        return new SessionCreationRequestDto(CODE, USER_ID, VEHICLE_ID);
    }

    private static User user(UUID userId) {
        User user = new User();
        user.setUserId(userId);
        return user;
    }

    private static ParkingFacility facility(ParkingFacilityStatus status) {
        ParkingFacility facility = new ParkingFacility();
        facility.setFacilityId(UUID.randomUUID());
        facility.setFacilityCode(CODE);
        facility.setStatus(status);
        facility.setPricingPolicy(POLICY);
        return facility;
    }

    private static Vehicle vehicle(UUID ownerId) {
        Vehicle vehicle = new Vehicle();
        vehicle.setVehicleId(VEHICLE_ID);
        vehicle.setVehicleType(VehicleType.LMV);
        vehicle.setUser(user(ownerId));
        return vehicle;
    }

    private static ParkingSession session(SessionStatus status) {
        ParkingSession session = new ParkingSession();
        session.setSessionId(SESSION_ID);
        session.setFacility(facility(ACTIVE));
        session.setVehicle(vehicle(USER_ID));
        session.setUser(user(USER_ID));
        session.setSessionStatus(status);
        return session;
    }

    /** Parked two minutes ago on an LMV at ₹0.010/s. */
    private static ParkingSession parkedSession() {
        ParkingSession session = session(SessionStatus.PARKED);
        session.setEntryTimestamp(Instant.now().minusSeconds(120));
        return session;
    }
}
