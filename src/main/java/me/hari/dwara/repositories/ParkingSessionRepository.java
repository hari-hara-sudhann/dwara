package me.hari.dwara.repositories;

import me.hari.dwara.entities.ParkingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ParkingSessionRepository extends JpaRepository<ParkingSession, UUID> {
    @Query("""
        SELECT p FROM ParkingSession p
        WHERE p.user.userId = :userId
    """)
    Optional<ParkingSession> findActiveSessionByUserId(UUID userId);
}
