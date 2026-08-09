package me.hari.dwara.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import me.hari.dwara.entities.enums.VehicleType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
public class Vehicle {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID vehicleId;

    @Column(unique = true)
    private String numberPlate;

    @ManyToOne
    @JoinColumn(name="user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    private VehicleType vehicleType;

    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
