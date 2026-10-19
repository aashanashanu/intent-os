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
        String message,
        TransactionProposal proposalSnapshot,
        String runId,
        String revisionId) {

    public IntentDecisionEvent(String eventId, String intentId, String proposalId, Instant occurredAt,
            String eventType, ValidationDecision decision, BigDecimal calculatedTotal,
            List<PolicyViolation> violations, String message) {
        this(eventId, intentId, proposalId, occurredAt, eventType, decision,
            calculatedTotal, violations, message, null, null, null);
        }

        public IntentDecisionEvent(String eventId, String intentId, String proposalId, Instant occurredAt,
            String eventType, ValidationDecision decision, BigDecimal calculatedTotal,
            List<PolicyViolation> violations, String message, TransactionProposal proposalSnapshot) {
        this(eventId, intentId, proposalId, occurredAt, eventType, decision,
            calculatedTotal, violations, message, proposalSnapshot, null, null);
    }

    public IntentDecisionEvent {
        violations = List.copyOf(violations);
    }
}
