package me.hari.dwara.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import me.hari.dwara.entities.enums.GateType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
public class GateController {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID controllerId;

    @ManyToOne
    @JoinColumn(name = "facility_id")
    private ParkingFacility facility;

    @Enumerated(EnumType.STRING)
    private GateType gateType;

    private String firmwareVersion;

    private Instant lastHeartbeat;
}
