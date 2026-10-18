package me.hari.dwara.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import me.hari.dwara.entities.enums.CommandStatus;
import me.hari.dwara.entities.enums.DeviceCommandType;

import java.time.Instant;
import java.util.UUID;

/**
 * One queued remote command for a gate (spec D11) — the only backend-initiated
 * channel in v1. The ESP32 polls {@code GET /devices/{id}/commands} and the
 * poll implicitly acks, so a row is {@link CommandStatus#PENDING} until it has
 * been handed out once.
 */
@Entity
@Getter
@Setter
public class DeviceCommand {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID commandId;

    @ManyToOne
    @JoinColumn(name = "controller_id", nullable = false)
    private GateController device;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeviceCommandType command;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CommandStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant deliveredAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
