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

        Instant start = session.getEntryTimestamp();
        Instant end = Instant.now();
        Duration duration = Duration.between(session.getEntryTimestamp(), Instant.now());
        PricingPolicy policy = session.getFacility().getPricingPolicy();

        BigDecimal priceRate = switch (session.getVehicle().getVehicleType()) {
            case MC -> policy.mcRate();
            case LMV -> policy.lmvRate();
            case HMV -> policy.hmvRate();
        };

        BigDecimal estimatedPrice = priceRate.multiply(BigDecimal.valueOf(duration.getSeconds()));

        return new SessionDto(
                session.getSessionId(),
                start,
                estimatedPrice
        );
    }
}
