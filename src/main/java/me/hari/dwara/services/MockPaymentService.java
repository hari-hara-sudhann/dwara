package me.hari.dwara.services;

import me.hari.dwara.dtos.payment.PaymentOutcome;
import me.hari.dwara.services.interfaces.PaymentService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * v1 payment stub (spec D10): every charge succeeds immediately and is stamped
 * with a {@code MOCK-<uuid>} reference. No money moves, and the session
 * lifecycle does not care that it is fake.
 */
@Service
public class MockPaymentService implements PaymentService {

    @Override
    public PaymentOutcome charge(UUID sessionId, BigDecimal amount) {
        return new PaymentOutcome(true, "MOCK-" + UUID.randomUUID());
    }
}
