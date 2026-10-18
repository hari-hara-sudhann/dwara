package me.hari.dwara.configs;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SessionPropertiesTest {

    @Test
    void defaultsMatchTheSpecWhenNothingIsConfigured() {
        SessionProperties properties = new SessionProperties(null, null);

        assertEquals(Duration.ofMinutes(15), properties.staleAfter(), "spec D20");
        assertEquals(Duration.ofSeconds(8), properties.exitGraceWindow(), "spec §10 #5");
    }

    @Test
    void configuredValuesWin() {
        SessionProperties properties = new SessionProperties(Duration.ofMinutes(5), Duration.ofSeconds(30));

        assertEquals(Duration.ofMinutes(5), properties.staleAfter());
        assertEquals(Duration.ofSeconds(30), properties.exitGraceWindow());
    }
}
