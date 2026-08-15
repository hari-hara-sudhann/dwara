package me.hari.dwara.mappers;

import me.hari.dwara.dtos.parkingsession.SessionCreationRequestDto;
import me.hari.dwara.entities.ParkingFacility;
import me.hari.dwara.entities.ParkingSession;
import me.hari.dwara.entities.User;
import me.hari.dwara.entities.Vehicle;
import me.hari.dwara.entities.enums.SessionStatus;

public class SessionMapper {
    public static ParkingSession creationRequestToSession(SessionCreationRequestDto dto, User user, ParkingFacility facility, Vehicle vehicle) {
        ParkingSession session = new ParkingSession();
        session.setUser(user);
        session.setFacility(facility);
        session.setVehicle(vehicle);
        session.setSessionStatus(SessionStatus.CREATED);
        return session;
    }
}
