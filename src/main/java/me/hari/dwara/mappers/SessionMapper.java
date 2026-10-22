package me.hari.dwara.mappers;

import me.hari.dwara.dtos.parkingsession.SessionCreationRequestDto;
import me.hari.dwara.dtos.parkingsession.SessionDto;
import me.hari.dwara.entities.ParkingFacility;
import me.hari.dwara.entities.ParkingSession;
import me.hari.dwara.entities.User;
import me.hari.dwara.entities.Vehicle;
import me.hari.dwara.entities.enums.SessionStatus;
import me.hari.dwara.entities.records.PricingPolicy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

public class SessionMapper {
    public static ParkingSession creationRequestToSession(SessionCreationRequestDto dto, User user, ParkingFacility facility, Vehicle vehicle) {
        ParkingSession session = new ParkingSession();
        session.setUser(user);
        session.setFacility(facility);
        session.setVehicle(vehicle);
        session.setSessionStatus(SessionStatus.CREATED);
        return session;
    }

    public static SessionDto toDto(ParkingSession session) {

        // A CREATED session (started in the app, not yet authorized at the
        // gate) has no entryTimestamp yet: the backend clock starts only when
        // the gate opens (spec D3/D19). Report zero rather than NPE so
        // GET /sessions/active works in that state too.
        Instant start = session.getEntryTimestamp();

        BigDecimal estimatedPrice = BigDecimal.ZERO;
        if (start != null) {
            Duration duration = Duration.between(start, Instant.now());
            PricingPolicy policy = session.getFacility().getPricingPolicy();
            BigDecimal priceRate = policy.getRate(session.getVehicle().getVehicleType());
            estimatedPrice = priceRate.multiply(BigDecimal.valueOf(duration.getSeconds()));
        }

        return new SessionDto(
                session.getSessionId(),
                session.getSessionStatus(),
                session.getFacility().getFacilityCode(),
                session.getFacility().getName(),
                session.getVehicle().getVehicleId(),
                session.getVehicle().getNumberPlate(),
                session.getCreatedAt(),
                start,
                session.getExitTimestamp(),
                estimatedPrice,
                session.getTotalFee()
        );
    }
}
