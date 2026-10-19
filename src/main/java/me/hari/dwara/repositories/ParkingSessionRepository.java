package me.hari.dwara.repositories;

import me.hari.dwara.entities.ParkingSession;
import me.hari.dwara.entities.enums.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ParkingSessionRepository extends JpaRepository<ParkingSession, UUID> {
    // One of the ACTIVE_STATUSES only — a user's EXITED/ABORTED history must
    // never be reported as their current session (spec §3.2/§5).
    Optional<ParkingSession> findFirstByUser_UserIdAndSessionStatusInOrderByCreatedAtDesc(
            UUID userId, Collection<SessionStatus> statuses);

    // Spec D9: one active session per user AND per vehicle. The caller passes
    // the set of statuses that count as "active".
    boolean existsByUser_UserIdAndSessionStatusIn(UUID userId, Collection<SessionStatus> statuses);

    boolean existsByVehicle_VehicleIdAndSessionStatusIn(UUID vehicleId, Collection<SessionStatus> statuses);

    // Spec D20: CREATED sessions the user never validated at a gate.
    List<ParkingSession> findBySessionStatusAndCreatedAtBefore(SessionStatus sessionStatus, Instant cutoff);
}
