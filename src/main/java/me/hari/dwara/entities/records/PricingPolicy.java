package me.hari.dwara.entities.records;

import me.hari.dwara.entities.enums.VehicleType;

import java.math.BigDecimal;

public record PricingPolicy(
        BigDecimal mcRate,
        BigDecimal lmvRate,
        BigDecimal hmvRate
) {
    public BigDecimal getRate(VehicleType type) {
        return switch (type) {
            case MC -> mcRate;
            case LMV -> lmvRate;
            case HMV -> hmvRate;
        };
    }
}
