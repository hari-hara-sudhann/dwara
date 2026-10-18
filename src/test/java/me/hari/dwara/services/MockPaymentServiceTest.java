package me.hari.dwara.services;

import me.hari.dwara.dtos.payment.PaymentOutcome;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockPaymentServiceTest {

    private final MockPaymentService service = new MockPaymentService();

    @Test
    void charge_alwaysSucceedsWithAMockReference() {
        PaymentOutcome outcome = service.charge(UUID.randomUUID(), new BigDecimal("12.34"));

        assertTrue(outcome.successful());
        assertTrue(outcome.transactionReference().startsWith("MOCK-"));
    }

    @Test
    void charge_stampsEveryAttemptWithItsOwnReference() {
        UUID sessionId = UUID.randomUUID();

        String first = service.charge(sessionId, new BigDecimal("1.00")).transactionReference();
        String second = service.charge(sessionId, new BigDecimal("1.00")).transactionReference();

        assertNotEquals(first, second);
    }
}
