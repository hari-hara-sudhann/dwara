package me.hari.dwara.services;

import me.hari.dwara.dtos.ResponseObject;
import me.hari.dwara.dtos.gate.PendingCommandDto;
import me.hari.dwara.entities.DeviceCommand;
import me.hari.dwara.entities.enums.CommandStatus;
import me.hari.dwara.entities.enums.DeviceCommandType;
import me.hari.dwara.repositories.DeviceCommandRepository;
import me.hari.dwara.repositories.GateControllerRepository;
import me.hari.dwara.services.interfaces.GateControllerClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GateCommandServiceTest {

    private static final UUID DEVICE_ID = UUID.randomUUID();

    @Mock
    private GateControllerRepository gateControllerRepository;
    @Mock
    private DeviceCommandRepository deviceCommandRepository;
    @Mock
    private GateControllerClient gateControllerClient;

    private GateCommandService service;

    @BeforeEach
    void setUp() {
        service = new GateCommandService(
                gateControllerRepository, deviceCommandRepository, gateControllerClient);
    }

    // ---------------------------------------------------------- requestOpen --

    @Test
    void requestOpen_refusesAnUnregisteredDevice() {
        when(gateControllerClient.openGate(DEVICE_ID)).thenReturn(false);

        ResponseObject<Void> result = service.requestOpen(DEVICE_ID);

        assertFalse(result.getSuccess());
        assertEquals("Gate controller does not exist.", result.getMessage());
    }

    @Test
    void requestOpen_queuesThroughTheClient() {
        when(gateControllerClient.openGate(DEVICE_ID)).thenReturn(true);

        ResponseObject<Void> result = service.requestOpen(DEVICE_ID);

        assertTrue(result.getSuccess());
        verify(gateControllerClient).openGate(DEVICE_ID);
    }

    // ---------------------------------------------------- requestKeepClosed --

    @Test
    void requestKeepClosed_refusesAnUnregisteredDevice() {
        when(gateControllerClient.keepClosed(DEVICE_ID)).thenReturn(false);

        assertFalse(service.requestKeepClosed(DEVICE_ID).getSuccess());
    }

    @Test
    void requestKeepClosed_queuesThroughTheClient() {
        when(gateControllerClient.keepClosed(DEVICE_ID)).thenReturn(true);

        assertTrue(service.requestKeepClosed(DEVICE_ID).getSuccess());
        verify(gateControllerClient).keepClosed(DEVICE_ID);
    }

    // --------------------------------------------------------- pollCommands --

    @Test
    void pollCommands_refusesAnUnregisteredDevice() {
        when(gateControllerRepository.existsById(DEVICE_ID)).thenReturn(false);

        ResponseObject<PendingCommandDto> result = service.pollCommands(DEVICE_ID);

        assertFalse(result.getSuccess());
        verify(deviceCommandRepository, never())
                .findFirstByDevice_ControllerIdAndStatusOrderByCreatedAtAsc(any(), any());
    }

    @Test
    void pollCommands_answersNoneWhenNothingIsQueued() {
        when(gateControllerRepository.existsById(DEVICE_ID)).thenReturn(true);
        when(deviceCommandRepository
                .findFirstByDevice_ControllerIdAndStatusOrderByCreatedAtAsc(DEVICE_ID, CommandStatus.PENDING))
                .thenReturn(Optional.empty());

        ResponseObject<PendingCommandDto> result = service.pollCommands(DEVICE_ID);

        assertTrue(result.getSuccess());
        assertEquals(DeviceCommandType.NONE, result.getResponse().command());
        verify(deviceCommandRepository, never()).save(any());
    }

    @Test
    void pollCommands_handsOverTheOldestPendingCommandAndAcksIt() {
        DeviceCommand queued = new DeviceCommand();
        queued.setCommand(DeviceCommandType.OPEN);
        queued.setStatus(CommandStatus.PENDING);

        when(gateControllerRepository.existsById(DEVICE_ID)).thenReturn(true);
        when(deviceCommandRepository
                .findFirstByDevice_ControllerIdAndStatusOrderByCreatedAtAsc(DEVICE_ID, CommandStatus.PENDING))
                .thenReturn(Optional.of(queued));

        ResponseObject<PendingCommandDto> result = service.pollCommands(DEVICE_ID);

        assertTrue(result.getSuccess());
        assertEquals(DeviceCommandType.OPEN, result.getResponse().command());
        assertEquals(CommandStatus.DELIVERED, queued.getStatus());
        assertNotNull(queued.getDeliveredAt(), "the poll itself is the acknowledgement");
        verify(deviceCommandRepository).save(queued);
    }
}
