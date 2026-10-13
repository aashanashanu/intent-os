package com.intentos.intent;

import com.intentos.validation.PolicyViolation;
import com.intentos.validation.ValidationDecision;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record IntentDecisionEvent(
        String eventId,
        String intentId,
        String proposalId,
        Instant occurredAt,
        String eventType,
        ValidationDecision decision,
        BigDecimal calculatedTotal,
        List<PolicyViolation> violations,
        String message) {
    public IntentDecisionEvent {
        violations = List.copyOf(violations);
    }
}
