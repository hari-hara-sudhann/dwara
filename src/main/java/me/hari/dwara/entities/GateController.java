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

    // Write-only from the API's point of view (spec §4): the ESP32 authenticates
    // with the plaintext secret, which is flashed at build time (D7) and never
    // readable back. Only the hash is stored (spec D6/D11).
    private String deviceSecretHash;
}
