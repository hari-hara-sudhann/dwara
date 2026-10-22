package me.hari.dwara.controllers;

import lombok.RequiredArgsConstructor;
import me.hari.dwara.dtos.ResponseObject;
import me.hari.dwara.dtos.parkingsession.SessionCreationRequestDto;
import me.hari.dwara.dtos.parkingsession.SessionDto;
import me.hari.dwara.dtos.parkingsession.SessionPageDto;
import me.hari.dwara.dtos.payment.PaymentReceiptDto;
import me.hari.dwara.entities.enums.SessionStatus;
import me.hari.dwara.services.ParkingSessionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * User-facing session lifecycle (spec §5, §3.2–§3.5).
 *
 * <p>Every endpoint takes the user from the JWT. The ownership-aware service
 * calls are what stop one account from reading, paying for or cancelling
 * another account's session.
 */
@RestController
@RequestMapping("/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final ParkingSessionService sessionService;

    @PostMapping
    public ResponseObject<UUID> create(
            @AuthenticationPrincipal UUID userId,
            @RequestBody SessionCreationRequestDto dto
        ) {

        if (dto.facilityCode() == null || dto.vehicleId() == null) {
            return ResponseObject.failure("facilityCode and vehicleId are required.");
        }

        return sessionService.createSession(
                new SessionCreationRequestDto(dto.facilityCode(), userId, dto.vehicleId()));
    }

    @GetMapping("/active")
    public ResponseObject<SessionDto> active(@AuthenticationPrincipal UUID userId) {
        return sessionService.getActiveSession(userId);
    }

    /**
     * Session history for the Activity screen: the caller's own sessions only,
     * newest first, optionally filtered by status and paged.
     */
    @GetMapping
    public ResponseObject<SessionPageDto> history(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(required = false) SessionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return sessionService.listSessions(userId, status, page, size);
    }

    @GetMapping("/{sessionId}/fee")
    public ResponseObject<BigDecimal> fee(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID sessionId) {

        return sessionService.getParkingFee(sessionId, userId);
    }

    @PostMapping("/{sessionId}/pay")
    public ResponseObject<PaymentReceiptDto> pay(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID sessionId) {

        return sessionService.pay(sessionId, userId);
    }

    @PostMapping("/{sessionId}/cancel")
    public ResponseObject<Void> cancel(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID sessionId) {

        return sessionService.cancel(sessionId, userId);
    }
}
