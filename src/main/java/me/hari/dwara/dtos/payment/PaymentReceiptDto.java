package me.hari.dwara.dtos.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Receipt for a completed payment (spec §3.5 step 2) — the frozen fee, not a
 * live estimate.
 */
public record PaymentReceiptDto(
        UUID paymentId,
        UUID sessionId,
        BigDecimal amount,
        String transactionReference,
        Instant paidAt
) {
}
