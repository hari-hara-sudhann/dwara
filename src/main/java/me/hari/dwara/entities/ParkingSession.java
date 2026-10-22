package me.hari.dwara.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import me.hari.dwara.entities.enums.SessionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
public class ParkingSession {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID sessionId;

    @ManyToOne
    @JoinColumn(name = "facilityId", nullable = false)
    private ParkingFacility facility;

    @ManyToOne
    @JoinColumn(name = "userId", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "vehicleId", nullable = false)
    private Vehicle vehicle;

    private Instant entryTimestamp;
    private Instant exitTimestamp;

    // When the backend authorized the EXIT gate. Distinct from exitTimestamp in
    // practice they are the same instant; the field exists so the grace-window
    // check (spec §10 #5) is measured from the gate open, never from payment
    // success. Older rows without it simply never re-open.
    private Instant exitGateOpenedAt;

    @Enumerated(EnumType.STRING)
    private SessionStatus sessionStatus;

    private BigDecimal totalFee;

    // When the session row was created — i.e. when the user tapped "start",
    // not when the gate opened. This is what the stale-session job measures
    // against (spec D20); entryTimestamp cannot, because it is only stamped
    // once the gate authorizes.
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
