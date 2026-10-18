package me.hari.dwara.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.hari.dwara.configs.SessionProperties;
import me.hari.dwara.dtos.ResponseObject;
import me.hari.dwara.dtos.gate.GateDecisionDto;
import me.hari.dwara.entities.GateController;
import me.hari.dwara.entities.ParkingFacility;
import me.hari.dwara.entities.ParkingSession;
import me.hari.dwara.entities.Payment;
import me.hari.dwara.entities.enums.DenyReason;
import me.hari.dwara.entities.enums.GateDecision;
import me.hari.dwara.entities.enums.PaymentStatus;
import me.hari.dwara.entities.enums.SessionStatus;
import me.hari.dwara.repositories.GateControllerRepository;
import me.hari.dwara.repositories.ParkingSessionRepository;
import me.hari.dwara.repositories.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Decision table for the gate authorization service (spec §3.3, §3.5, §10).
 */
@ExtendWith(MockitoExtension.class)
class GateControllerServiceImplTest {

    private static final UUID DEVICE_ID = UUID.randomUUID();
    private static final UUID SESSION_ID = UUID.randomUUID();

    @Mock
    private GateControllerRepository gateControllerRepository;
    @Mock
    private ParkingSessionRepository sessionRepository;
    @Mock
    private PaymentRepository paymentRepository;

    private GateControllerServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new GateControllerServiceImpl(
                gateControllerRepository,
                sessionRepository,
                paymentRepository,
                new SessionProperties(Duration.ofMinutes(15), Duration.ofSeconds(8))
        );
    }

    // ---------------------------------------------------------------- entry --

    @Test
    void entry_opensCreatedSessionAndStartsBackendBillingClock() {
        ParkingFacility facility = facility();
        stubDeviceAt(facility);
        ParkingSession session = session(facility, SessionStatus.CREATED);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        ResponseObject<GateDecisionDto> result = service.openEntryGate(DEVICE_ID, SESSION_ID);

        assertEquals(GateDecision.OPEN, result.getResponse().decision());
        assertEquals(DenyReason.NONE, result.getResponse().reason());
        assertEquals(SessionStatus.PARKED, session.getSessionStatus());
        assertNotNull(session.getEntryTimestamp(), "backend must stamp entryTimestamp (D3/D19)");
        verify(sessionRepository).save(session);
    }

    @Test
    void entry_refusesUnknownSession() {
        stubDeviceAt(facility());
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.empty());

        ResponseObject<GateDecisionDto> result = service.openEntryGate(DEVICE_ID, SESSION_ID);

        assertEquals(GateDecision.KEEP_CLOSED, result.getResponse().decision());
        assertEquals(DenyReason.UNKNOWN_SESSION, result.getResponse().reason());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void entry_refusesSessionBelongingToAnotherFacility() {
        stubDeviceAt(facility());
        ParkingSession session = session(facility(), SessionStatus.CREATED);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        ResponseObject<GateDecisionDto> result = service.openEntryGate(DEVICE_ID, SESSION_ID);

        assertEquals(DenyReason.WRONG_FACILITY, result.getResponse().reason());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void entry_refusesSessionWithNoFacilityOnTheDevice() {
        ParkingFacility facility = facility();
        GateController device = new GateController();
        device.setControllerId(DEVICE_ID);
        when(gateControllerRepository.findById(DEVICE_ID)).thenReturn(Optional.of(device));
        ParkingSession session = session(facility, SessionStatus.CREATED);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        ResponseObject<GateDecisionDto> result = service.openEntryGate(DEVICE_ID, SESSION_ID);

        assertEquals(DenyReason.WRONG_FACILITY, result.getResponse().reason());
    }

    @Test
    void entry_refusesSessionWithNoFacility() {
        stubDeviceAt(facility());
        ParkingSession session = new ParkingSession();
        session.setSessionId(SESSION_ID);
        session.setSessionStatus(SessionStatus.CREATED);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        ResponseObject<GateDecisionDto> result = service.openEntryGate(DEVICE_ID, SESSION_ID);

        assertEquals(DenyReason.WRONG_FACILITY, result.getResponse().reason());
    }

    @Test
    void entry_refusesAbortedSession() {
        ParkingFacility facility = facility();
        stubDeviceAt(facility);
        ParkingSession session = session(facility, SessionStatus.ABORTED);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        ResponseObject<GateDecisionDto> result = service.openEntryGate(DEVICE_ID, SESSION_ID);

        assertEquals(DenyReason.STALE_SESSION, result.getResponse().reason());
    }

    @Test
    void entry_refusesSessionThatIsAlreadyParked() {
        ParkingFacility facility = facility();
        stubDeviceAt(facility);
        ParkingSession session = session(facility, SessionStatus.PARKED);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        ResponseObject<GateDecisionDto> result = service.openEntryGate(DEVICE_ID, SESSION_ID);

        assertEquals(DenyReason.WRONG_STATE_FOR_ENTRY, result.getResponse().reason());
    }

    @Test
    void entry_failsForUnregisteredDevice() {
        when(gateControllerRepository.findById(DEVICE_ID)).thenReturn(Optional.empty());

        ResponseObject<GateDecisionDto> result = service.openEntryGate(DEVICE_ID, SESSION_ID);

        assertFalse(result.getSuccess());
        assertNull(result.getResponse());
        verify(sessionRepository, never()).findById(any());
    }

    // ----------------------------------------------------------------- exit --

    @Test
    void exit_opensPaidSessionAndMarksItExited() {
        ParkingFacility facility = facility();
        stubDeviceAt(facility);
        ParkingSession session = session(facility, SessionStatus.PAYMENT_PENDING);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(paymentRepository.findBySession(session)).thenReturn(Optional.of(payment(PaymentStatus.SUCCESS)));

        ResponseObject<GateDecisionDto> result = service.openExitGate(DEVICE_ID, SESSION_ID);

        assertEquals(GateDecision.OPEN, result.getResponse().decision());
        assertEquals(DenyReason.NONE, result.getResponse().reason());
        assertEquals(SessionStatus.EXITED, session.getSessionStatus());
        verify(sessionRepository).save(session);
    }

    @Test
    void exit_refusesPendingSessionWithoutSuccessfulPayment() {
        ParkingFacility facility = facility();
        stubDeviceAt(facility);
        ParkingSession session = session(facility, SessionStatus.PAYMENT_PENDING);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(paymentRepository.findBySession(session)).thenReturn(Optional.of(payment(PaymentStatus.PENDING)));

        ResponseObject<GateDecisionDto> result = service.openExitGate(DEVICE_ID, SESSION_ID);

        assertEquals(DenyReason.PAYMENT_REQUIRED, result.getResponse().reason());
        assertEquals(SessionStatus.PAYMENT_PENDING, session.getSessionStatus());
    }

    @Test
    void exit_refusesPendingSessionWithoutAnyPaymentRow() {
        ParkingFacility facility = facility();
        stubDeviceAt(facility);
        ParkingSession session = session(facility, SessionStatus.PAYMENT_PENDING);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(paymentRepository.findBySession(session)).thenReturn(Optional.empty());

        ResponseObject<GateDecisionDto> result = service.openExitGate(DEVICE_ID, SESSION_ID);

        assertEquals(DenyReason.PAYMENT_REQUIRED, result.getResponse().reason());
    }

    @Test
    void exit_refusesParkedSessionWithoutAnyPaymentRow() {
        ParkingFacility facility = facility();
        stubDeviceAt(facility);
        ParkingSession session = session(facility, SessionStatus.PARKED);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        ResponseObject<GateDecisionDto> result = service.openExitGate(DEVICE_ID, SESSION_ID);

        assertEquals(DenyReason.PAYMENT_REQUIRED, result.getResponse().reason());
        verify(paymentRepository, never()).findBySession(any());
    }

    @Test
    void exit_refusesSessionStillInCreatedState() {
        ParkingFacility facility = facility();
        stubDeviceAt(facility);
        ParkingSession session = session(facility, SessionStatus.CREATED);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        ResponseObject<GateDecisionDto> result = service.openExitGate(DEVICE_ID, SESSION_ID);

        assertEquals(DenyReason.PAYMENT_REQUIRED, result.getResponse().reason());
    }

    @Test
    void exit_graceReopensSessionExitedMomentsAgo() {
        ParkingFacility facility = facility();
        stubDeviceAt(facility);
        ParkingSession session = session(facility, SessionStatus.EXITED);
        session.setExitTimestamp(Instant.now().minusSeconds(2));
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        ResponseObject<GateDecisionDto> result = service.openExitGate(DEVICE_ID, SESSION_ID);

        assertEquals(GateDecision.OPEN, result.getResponse().decision());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void exit_refusesSessionExitedOutsideTheGraceWindow() {
        ParkingFacility facility = facility();
        stubDeviceAt(facility);
        ParkingSession session = session(facility, SessionStatus.EXITED);
        session.setExitTimestamp(Instant.now().minusSeconds(60));
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        ResponseObject<GateDecisionDto> result = service.openExitGate(DEVICE_ID, SESSION_ID);

        assertEquals(DenyReason.STALE_SESSION, result.getResponse().reason());
    }

    @Test
    void exit_refusesExitedSessionWithNoExitTimestamp() {
        ParkingFacility facility = facility();
        stubDeviceAt(facility);
        ParkingSession session = session(facility, SessionStatus.EXITED);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        ResponseObject<GateDecisionDto> result = service.openExitGate(DEVICE_ID, SESSION_ID);

        assertEquals(DenyReason.STALE_SESSION, result.getResponse().reason());
    }

    @Test
    void exit_refusesAbortedSession() {
        ParkingFacility facility = facility();
        stubDeviceAt(facility);
        ParkingSession session = session(facility, SessionStatus.ABORTED);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        ResponseObject<GateDecisionDto> result = service.openExitGate(DEVICE_ID, SESSION_ID);

        assertEquals(DenyReason.STALE_SESSION, result.getResponse().reason());
    }

    @Test
    void exit_refusesSessionBelongingToAnotherFacility() {
        stubDeviceAt(facility());
        ParkingSession session = session(facility(), SessionStatus.PAYMENT_PENDING);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        ResponseObject<GateDecisionDto> result = service.openExitGate(DEVICE_ID, SESSION_ID);

        assertEquals(DenyReason.WRONG_FACILITY, result.getResponse().reason());
        verify(paymentRepository, never()).findBySession(any());
    }

    @Test
    void exit_refusesUnknownSession() {
        stubDeviceAt(facility());
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.empty());

        ResponseObject<GateDecisionDto> result = service.openExitGate(DEVICE_ID, SESSION_ID);

        assertEquals(DenyReason.UNKNOWN_SESSION, result.getResponse().reason());
    }

    @Test
    void exit_failsForUnregisteredDevice() {
        when(gateControllerRepository.findById(DEVICE_ID)).thenReturn(Optional.empty());

        ResponseObject<GateDecisionDto> result = service.openExitGate(DEVICE_ID, SESSION_ID);

        assertFalse(result.getSuccess());
    }

    // --------------------------------------------------------- wire format --

    @Test
    void decisionSerialisesToTheStringsTheFirmwareParses() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        String open = mapper.writeValueAsString(new GateDecisionDto(GateDecision.OPEN, DenyReason.NONE));
        String refused = mapper.writeValueAsString(
                new GateDecisionDto(GateDecision.KEEP_CLOSED, DenyReason.PAYMENT_REQUIRED));

        assertEquals("{\"decision\":\"open\",\"reason\":\"NONE\"}", open);
        assertEquals("{\"decision\":\"keep_closed\",\"reason\":\"PAYMENT_REQUIRED\"}", refused);
    }

    // ------------------------------------------------------------- helpers --

    private static ParkingFacility facility() {
        ParkingFacility facility = new ParkingFacility();
        facility.setFacilityId(UUID.randomUUID());
        return facility;
    }

    private void stubDeviceAt(ParkingFacility facility) {
        GateController device = new GateController();
        device.setControllerId(DEVICE_ID);
        device.setFacility(facility);
        when(gateControllerRepository.findById(DEVICE_ID)).thenReturn(Optional.of(device));
    }

    private static ParkingSession session(ParkingFacility facility, SessionStatus status) {
        ParkingSession session = new ParkingSession();
        session.setSessionId(SESSION_ID);
        session.setFacility(facility);
        session.setSessionStatus(status);
        return session;
    }

    private static Payment payment(PaymentStatus status) {
        Payment payment = new Payment();
        payment.setStatus(status);
        return payment;
    }
}
