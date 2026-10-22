package me.hari.dwara.repositories;

import me.hari.dwara.entities.ParkingSession;
import me.hari.dwara.entities.enums.SessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
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

    // GET /sessions history: own sessions, newest first, optionally filtered.
    Page<ParkingSession> findByUser_UserIdAndSessionStatusIn(
            UUID userId, Collection<SessionStatus> statuses, Pageable pageable);

    /**
     * Row lock for payment: two concurrent pay calls must not both charge.
     * Requires an active transaction (the service method is @Transactional).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ParkingSession s where s.sessionId = :sessionId")
    Optional<ParkingSession> findByIdForUpdate(@Param("sessionId") UUID sessionId);
}
