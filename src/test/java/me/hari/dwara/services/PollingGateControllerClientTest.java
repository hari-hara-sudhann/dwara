package me.hari.dwara.services;

import me.hari.dwara.entities.DeviceCommand;
import me.hari.dwara.entities.GateController;
import me.hari.dwara.entities.enums.CommandStatus;
import me.hari.dwara.entities.enums.DeviceCommandType;
import me.hari.dwara.repositories.DeviceCommandRepository;
import me.hari.dwara.repositories.GateControllerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PollingGateControllerClientTest {

    private static final UUID DEVICE_ID = UUID.randomUUID();

    @Mock
    private GateControllerRepository gateControllerRepository;
    @Mock
    private DeviceCommandRepository deviceCommandRepository;

    private PollingGateControllerClient client;

    @BeforeEach
    void setUp() {
        client = new PollingGateControllerClient(gateControllerRepository, deviceCommandRepository);
    }

    @Test
    void openGate_reportsFailureForAnUnregisteredDevice() {
        when(gateControllerRepository.findById(DEVICE_ID)).thenReturn(Optional.empty());

        assertFalse(client.openGate(DEVICE_ID));
        verify(deviceCommandRepository, never()).save(any());
    }

    @Test
    void openGate_queuesAPendingOpenForTheDevice() {
        GateController device = device();
        when(gateControllerRepository.findById(DEVICE_ID)).thenReturn(Optional.of(device));

        assertTrue(client.openGate(DEVICE_ID));

        ArgumentCaptor<DeviceCommand> queued = ArgumentCaptor.forClass(DeviceCommand.class);
        verify(deviceCommandRepository).save(queued.capture());
        assertEquals(DeviceCommandType.OPEN, queued.getValue().getCommand());
        assertEquals(CommandStatus.PENDING, queued.getValue().getStatus());
        assertEquals(device, queued.getValue().getDevice());
    }

    @Test
    void keepClosed_queuesAPendingKeepClosed() {
        when(gateControllerRepository.findById(DEVICE_ID)).thenReturn(Optional.of(device()));

        assertTrue(client.keepClosed(DEVICE_ID));

        ArgumentCaptor<DeviceCommand> queued = ArgumentCaptor.forClass(DeviceCommand.class);
        verify(deviceCommandRepository).save(queued.capture());
        assertEquals(DeviceCommandType.KEEP_CLOSED, queued.getValue().getCommand());
    }

    @Test
    void keepClosed_reportsFailureForAnUnregisteredDevice() {
        when(gateControllerRepository.findById(DEVICE_ID)).thenReturn(Optional.empty());

        assertFalse(client.keepClosed(DEVICE_ID));
    }

    @Test
    void isAvailable_tracksWhetherTheDeviceIsRegistered() {
        when(gateControllerRepository.existsById(DEVICE_ID)).thenReturn(true);
        assertTrue(client.isAvailable(DEVICE_ID));
    }

    @Test
    void isAvailable_isFalseForAnUnregisteredDevice() {
        when(gateControllerRepository.existsById(DEVICE_ID)).thenReturn(false);
        assertFalse(client.isAvailable(DEVICE_ID));
    }

    private static GateController device() {
        GateController device = new GateController();
        device.setControllerId(DEVICE_ID);
        return device;
    }
}
