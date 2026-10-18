package me.hari.dwara.repositories;

import me.hari.dwara.entities.ParkingSession;
import me.hari.dwara.entities.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    // Payment ↔ session is one-to-one (session_id is unique), so at most one row.
    Optional<Payment> findBySession(ParkingSession session);
}
