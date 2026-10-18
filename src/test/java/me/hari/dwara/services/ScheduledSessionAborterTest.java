package me.hari.dwara.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledSessionAborterTest {

    @Mock
    private ParkingSessionService sessionService;

    @InjectMocks
    private ScheduledSessionAborter job;

    @Test
    void sweepStaleSessions_runsQuietlyWhenNothingIsStale() {
        when(sessionService.abortStaleSessions()).thenReturn(0);

        job.sweepStaleSessions();

        verify(sessionService).abortStaleSessions();
    }

    @Test
    void sweepStaleSessions_delegatesTheSweep() {
        when(sessionService.abortStaleSessions()).thenReturn(3);

        job.sweepStaleSessions();

        verify(sessionService).abortStaleSessions();
    }
}
