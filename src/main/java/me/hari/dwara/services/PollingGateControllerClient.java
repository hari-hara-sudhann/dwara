package me.hari.dwara.services;

import lombok.RequiredArgsConstructor;
import me.hari.dwara.entities.DeviceCommand;
import me.hari.dwara.entities.GateController;
import me.hari.dwara.entities.enums.CommandStatus;
import me.hari.dwara.entities.enums.DeviceCommandType;
import me.hari.dwara.repositories.DeviceCommandRepository;
import me.hari.dwara.repositories.GateControllerRepository;
import me.hari.dwara.services.interfaces.GateControllerClient;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Queue-backed {@link GateControllerClient} for the v1 firmware.
 *
 * <p>This firmware cannot be pushed to: it polls {@code GET
 * /devices/{id}/commands} on a timer (spec D11). So "sending" a command means
 * persisting a {@link CommandStatus#PENDING} row that the next poll picks up —
 * the queue <em>is</em> the transport. If a future firmware variant gets a
 * push channel (MQTT, WebSocket), it slots in here and no caller changes.
 */
@Service
@RequiredArgsConstructor
public class PollingGateControllerClient implements GateControllerClient {

    private final GateControllerRepository gateControllerRepository;
    private final DeviceCommandRepository deviceCommandRepository;

    @Override
    public boolean openGate(UUID deviceId) {
        return enqueue(deviceId, DeviceCommandType.OPEN);
    }

    @Override
    public boolean keepClosed(UUID deviceId) {
        return enqueue(deviceId, DeviceCommandType.KEEP_CLOSED);
    }

    @Override
    public boolean isAvailable(UUID deviceId) {
        return gateControllerRepository.existsById(deviceId);
    }

    private boolean enqueue(UUID deviceId, DeviceCommandType command) {
        Optional<GateController> device = gateControllerRepository.findById(deviceId);
        if (device.isEmpty()) {
            return false;
        }

        DeviceCommand queued = new DeviceCommand();
        queued.setDevice(device.get());
        queued.setCommand(command);
        queued.setStatus(CommandStatus.PENDING);
        deviceCommandRepository.save(queued);

        return true;
    }
}
