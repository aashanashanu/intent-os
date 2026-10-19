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
        BigDecimal preventedAmount,
        String currency,
        String paypalOrderId,
        String paypalCaptureId,
        String orderStatus,
        String captureStatus,
        String webhookEventId,
        Instant createdAt,
        List<IntentDecisionEvent> activityTimeline,
        String searchText,
        String runId,
        String revisionId) {

    public AgentReceipt(String receiptId, String intentId, IntentContract intent,
            List<TransactionProposal> proposalHistory, List<PolicyViolation> violations,
            List<IntentDecisionEvent> repairs, ValidationDecision finalDecision, BigDecimal amount,
            BigDecimal preventedAmount, String currency, String paypalOrderId, String paypalCaptureId,
            String orderStatus, String captureStatus, String webhookEventId, Instant createdAt,
            List<IntentDecisionEvent> activityTimeline, String searchText) {
        this(receiptId, intentId, intent, proposalHistory, violations, repairs, finalDecision, amount,
                preventedAmount, currency, paypalOrderId, paypalCaptureId, orderStatus, captureStatus,
                webhookEventId, createdAt, activityTimeline, searchText, null, null);
    }

    public AgentReceipt {
        proposalHistory = List.copyOf(proposalHistory);
        violations = List.copyOf(violations);
        repairs = List.copyOf(repairs);
        activityTimeline = List.copyOf(activityTimeline);
        searchText = searchText == null ? "" : searchText;
        preventedAmount = preventedAmount == null ? BigDecimal.ZERO : preventedAmount;
    }
}