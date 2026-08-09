package me.hari.dwara.entities.records;

import java.math.BigDecimal;

public record PricingPolicy(
        BigDecimal mcRate,
        BigDecimal lmvRate,
        BigDecimal hmvRate
) {}
