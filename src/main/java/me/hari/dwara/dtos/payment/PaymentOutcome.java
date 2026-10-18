package me.hari.dwara.dtos.payment;

/**
 * What a {@link me.hari.dwara.services.interfaces.PaymentService} charge came
 * back with. {@code transactionReference} is only meaningful on success.
 */
public record PaymentOutcome(
        boolean successful,
        String transactionReference
) {
}
