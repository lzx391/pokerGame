package com.example.mgdemoplus.room.support;

import java.math.BigDecimal;

/** Immutable per-user honor / weekly stats delta produced at settle time. */
public record DpSettleStatsIncrement(
        int userId,
        int royalInc,
        int straightInc,
        int fourInc,
        BigDecimal handMultiplier) {
}
