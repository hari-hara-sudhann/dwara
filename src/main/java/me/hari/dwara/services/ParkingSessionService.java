package me.hari.dwara.services;

import me.hari.dwara.dtos.parkingsession.SessionDto;
import me.hari.dwara.entities.ParkingFacility;
import me.hari.dwara.entities.ParkingSession;
import me.hari.dwara.entities.User;
import me.hari.dwara.entities.Vehicle;
import me.hari.dwara.entities.enums.SessionStatus;
import me.hari.dwara.mappers.SessionMapper;
import me.hari.dwara.repositories.ParkingFacilityRepository;
import me.hari.dwara.repositories.UserRepository;
import me.hari.dwara.repositories.VehicleRepository;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import me.hari.dwara.dtos.ResponseObject;
import me.hari.dwara.dtos.parkingsession.SessionCreationRequestDto;
import me.hari.dwara.repositories.ParkingSessionRepository;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ParkingSessionService {
    private final ParkingSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final ParkingFacilityRepository facilityRepository;
    private final VehicleRepository vehicleRepository;

    public ResponseObject<UUID> createSession(SessionCreationRequestDto dto) {
        User user = userRepository.findById(dto.userId()).orElse(null);
        if (user == null)
            return ResponseObject.failure("User does not exist.");

        ParkingFacility facility = facilityRepository.findByFacilityCode(dto.facilityCode())
                .orElse(null);
        if (facility == null)
            return ResponseObject.failure("Parking facility does not exist.");

        Vehicle vehicle = vehicleRepository.findById(dto.vehicleId())
                .orElse(null);
        if (vehicle == null)
            return ResponseObject.failure("Vehicle does not exist.");

        if (!(vehicle.getUser().getUserId().equals(user.getUserId())))
            return ResponseObject.failure("Vehicle does not belong to the user.");

        ParkingSession session = SessionMapper.creationRequestToSession(dto, user, facility, vehicle);
        sessionRepository.save(session);

        return ResponseObject.success("Session successfully created", session.getSessionId());
    }

    public ResponseObject<Void> checkIn(UUID sessionId) {
        ParkingSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null)
            ResponseObject.failure("Parking session does not exist.");

        session.setEntryTimestamp(Instant.now());
        session.setSessionStatus(SessionStatus.PARKED);
        return ResponseObject.success("Parked successfully.", null);
    }

    public ResponseObject<SessionDto> getActiveSession(UUID userId) {
        ParkingSession session = sessionRepository.findActiveSessionByUserId(userId).orElse(null);
        if (session == null)
            return ResponseObject.failure("No active session found");

        SessionDto dto = SessionMapper.toDto(session);

        return ResponseObject.success("Active session found.", dto);
    }
}
