package com.intentos.paypal;

import com.intentos.intent.IntentContract;
import com.intentos.intent.IntentDecisionEvent;
import com.intentos.intent.TransactionProposal;
import com.intentos.validation.PolicyViolation;
import com.intentos.validation.ValidationDecision;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AgentReceipt(
        String receiptId,
        String intentId,
        IntentContract intent,
        List<TransactionProposal> proposalHistory,
        List<PolicyViolation> violations,
        List<IntentDecisionEvent> repairs,
        ValidationDecision finalDecision,
        BigDecimal amount,
        String currency,
        String paypalOrderId,
        String paypalCaptureId,
        String orderStatus,
        String captureStatus,
        String webhookEventId,
        Instant createdAt,
        List<IntentDecisionEvent> activityTimeline) {

    public AgentReceipt {
        proposalHistory = List.copyOf(proposalHistory);
        violations = List.copyOf(violations);
        repairs = List.copyOf(repairs);
        activityTimeline = List.copyOf(activityTimeline);
    }
}