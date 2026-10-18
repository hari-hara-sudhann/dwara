package me.hari.dwara.services;

import lombok.RequiredArgsConstructor;
import me.hari.dwara.dtos.ResponseObject;
import me.hari.dwara.dtos.gate.PendingCommandDto;
import me.hari.dwara.entities.DeviceCommand;
import me.hari.dwara.entities.enums.CommandStatus;
import me.hari.dwara.entities.enums.DeviceCommandType;
import me.hari.dwara.repositories.DeviceCommandRepository;
import me.hari.dwara.repositories.GateControllerRepository;
import me.hari.dwara.services.interfaces.GateControllerClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * The remote-command channel of spec D11 / §3.6 — the only backend-initiated
 * path in v1.
 *
 * <ul>
 *   <li>{@link #requestOpen}/{@link #requestKeepClosed} queue a command
 *       (attendant override, or the fail-open recovery of spec D16).</li>
 *   <li>{@link #pollCommands} is what the ESP32 calls; it hands over the oldest
 *       pending command and marks it delivered — the poll is the ack.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class GateCommandService {

    private final GateControllerRepository gateControllerRepository;
    private final DeviceCommandRepository deviceCommandRepository;
    private final GateControllerClient gateControllerClient;

    /** Queue an {@code OPEN} for the gate — attendant/override and D16 recovery. */
    public ResponseObject<Void> requestOpen(UUID deviceId) {
        if (!gateControllerClient.openGate(deviceId)) {
            return ResponseObject.failure("Gate controller does not exist.");
        }
        return ResponseObject.success("Open command queued.", null);
    }

    /** Queue a {@code KEEP_CLOSED} to hold a gate shut. */
    public ResponseObject<Void> requestKeepClosed(UUID deviceId) {
        if (!gateControllerClient.keepClosed(deviceId)) {
            return ResponseObject.failure("Gate controller does not exist.");
        }
        return ResponseObject.success("Keep-closed command queued.", null);
    }

    /**
     * Hand the firmware its next command.
     *
     * <p>Always answers with a decision-shaped payload: {@code NONE} when the
     * queue is empty, so the device never has to treat "nothing new" as an
     * error.
     */
    public ResponseObject<PendingCommandDto> pollCommands(UUID deviceId) {
        if (!gateControllerRepository.existsById(deviceId)) {
            return ResponseObject.failure("Gate controller does not exist.");
        }

        DeviceCommand pending = deviceCommandRepository
                .findFirstByDevice_ControllerIdAndStatusOrderByCreatedAtAsc(deviceId, CommandStatus.PENDING)
                .orElse(null);

        if (pending == null) {
            return ResponseObject.success(
                    "No pending command.",
                    new PendingCommandDto(DeviceCommandType.NONE)
            );
        }

        pending.setStatus(CommandStatus.DELIVERED);
        pending.setDeliveredAt(Instant.now());
        deviceCommandRepository.save(pending);

        return ResponseObject.success(
                "Command delivered.",
                new PendingCommandDto(pending.getCommand())
        );
    }
}
