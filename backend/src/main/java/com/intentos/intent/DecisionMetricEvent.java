package com.intentos.intent;

import com.intentos.validation.ValidationDecision;
import java.math.BigDecimal;
import java.time.Instant;

public record DecisionMetricEvent(
        String proposalId,
        String eventType,
        ValidationDecision decision,
        BigDecimal calculatedTotal,
        Instant occurredAt,
        String message) {}