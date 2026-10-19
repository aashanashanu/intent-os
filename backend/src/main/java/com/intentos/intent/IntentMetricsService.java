package com.intentos.intent;

import com.intentos.paypal.AgentReceipt;
import com.intentos.paypal.AgentReceiptStore;
import com.intentos.validation.ValidationDecision;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class IntentMetricsService {
    private final IntentDecisionEventStore eventStore;
    private final AgentReceiptStore receiptStore;

    public IntentMetricsService(IntentDecisionEventStore eventStore, AgentReceiptStore receiptStore) {
        this.eventStore = eventStore;
        this.receiptStore = receiptStore;
    }

    public IntentMetrics calculate(String intentId) {
        Map<String, List<DecisionMetricEvent>> proposals = new HashMap<>();
        eventStore.findMetricEventsByIntentId(intentId).stream()
                .filter(event -> event.proposalId() != null && !event.proposalId().isBlank())
            .forEach(event -> proposals.computeIfAbsent(event.runId() == null || event.runId().isBlank()
                ? "legacy:" + event.proposalId() : "run:" + event.runId(), ignored -> new ArrayList<>()).add(event));
        proposals.values().forEach(events -> events.sort(Comparator.comparing(
                DecisionMetricEvent::occurredAt, Comparator.nullsLast(Comparator.naturalOrder()))));

        BigDecimal capturedAmount = BigDecimal.ZERO;
        for (AgentReceipt receipt : receiptStore.findAllByIntentId(intentId)) {
            capturedAmount = capturedAmount.add(receipt.amount());
        }

        int approved = 0;
        int violating = 0;
        int repaired = 0;
        int requiresReview = 0;
        int firstPassCompliant = 0;
        int blockedExposure = 0;
        BigDecimal blockedExposureAmount = BigDecimal.ZERO;
        BigDecimal preventedAmount = BigDecimal.ZERO;
        for (List<DecisionMetricEvent> events : proposals.values()) {
            DecisionMetricEvent firstValidation = events.stream()
                .filter(event -> "SHOPPING_VALIDATION".equals(event.eventType())
                    || "VALIDATION".equals(event.eventType()))
                    .findFirst().orElse(events.get(0));
            List<DecisionMetricEvent> validations = events.stream()
                .filter(event -> "SHOPPING_VALIDATION".equals(event.eventType())
                    || "VALIDATION".equals(event.eventType())
                    || "REPAIR_COMPLETED".equals(event.eventType())).toList();
            DecisionMetricEvent finalEvent = validations.isEmpty() ? events.getLast() : validations.getLast();
            boolean hadViolation = events.stream().anyMatch(event -> event.violationCount() > 0
                || event.decision() == ValidationDecision.BLOCK);
            boolean hadRepair = events.stream().anyMatch(event -> event.eventType().startsWith("REPAIR_"));
                boolean needsReview = finalEvent.decision() == ValidationDecision.HOLD
                    || finalEvent.decision() == ValidationDecision.BLOCK
                    || requiresReview(finalEvent);
            boolean compliantFinal = finalEvent.decision() == ValidationDecision.APPROVE
                && finalEvent.violationCount() == 0;
            if (compliantFinal) approved++;
            if (hadViolation) violating++;
            if (hadViolation && hadRepair && compliantFinal) repaired++;
            if (needsReview) requiresReview++;
            if (firstValidation.decision() == ValidationDecision.APPROVE && firstValidation.violationCount() == 0) {
            firstPassCompliant++;
            }
            if (finalEvent.decision() == ValidationDecision.BLOCK || finalEvent.decision() == ValidationDecision.HOLD) {
            blockedExposure++;
                if (finalEvent.calculatedTotal() != null) {
                    blockedExposureAmount = blockedExposureAmount.add(finalEvent.calculatedTotal());
                }
            }

            BigDecimal originalAmount = firstValidation.calculatedTotal();
            if (originalAmount != null && compliantFinal && finalEvent.calculatedTotal() != null) {
            BigDecimal finalAmount = finalEvent.calculatedTotal();
                preventedAmount = preventedAmount.add(originalAmount.subtract(finalAmount).max(BigDecimal.ZERO));
            }
        }

        int total = proposals.size();
        return new IntentMetrics(total, approved, violating, repaired, requiresReview,
                preventedAmount, capturedAmount, rate(approved, total), rate(violating, total),
            rate(repaired, violating), rate(requiresReview, total), firstPassCompliant,
                rate(firstPassCompliant, total), blockedExposure, blockedExposureAmount);
    }

    private static boolean requiresReview(DecisionMetricEvent event) {
        return event.decision() == ValidationDecision.HOLD
                || (event.message() != null && event.message().toLowerCase(java.util.Locale.ROOT).contains("user review"));
    }

    private static BigDecimal rate(int numerator, int denominator) {
        if (denominator == 0) return BigDecimal.ZERO.setScale(4);
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }
}