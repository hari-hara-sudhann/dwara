package me.hari.dwara.services.interfaces;

import me.hari.dwara.dtos.payment.PaymentOutcome;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Collects a fee. Spec D10 keeps v1 on a mock implementation so the whole
 * flow is exercisable; swapping in a real gateway (Razorpay/Stripe) means
 * writing one new class and changing the injection point — nothing else in the
 * session lifecycle knows which one is in play.
 */
public interface PaymentService {
    /**
     * Attempt to collect {@code amount} (INR) for {@code sessionId}.
     *
     * <p>Implementations must not decide <em>whether</em> a session is payable —
     * that is the session service's state machine. This is only the charge.
     */
    PaymentOutcome charge(UUID sessionId, BigDecimal amount);
}
