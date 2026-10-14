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
                .forEach(event -> proposals.computeIfAbsent(event.proposalId(), ignored -> new ArrayList<>()).add(event));
        proposals.values().forEach(events -> events.sort(Comparator.comparing(
                DecisionMetricEvent::occurredAt, Comparator.nullsLast(Comparator.naturalOrder()))));

        Map<String, BigDecimal> capturedByProposal = new HashMap<>();
        BigDecimal capturedAmount = BigDecimal.ZERO;
        for (AgentReceipt receipt : receiptStore.findAllByIntentId(intentId)) {
            capturedAmount = capturedAmount.add(receipt.amount());
            receipt.proposalHistory().stream().findFirst().ifPresent(proposal ->
                    capturedByProposal.put(proposal.proposalId(), receipt.amount()));
        }

        int approved = 0;
        int violating = 0;
        int repaired = 0;
        int requiresReview = 0;
        BigDecimal preventedAmount = BigDecimal.ZERO;
        for (List<DecisionMetricEvent> events : proposals.values()) {
            DecisionMetricEvent firstValidation = events.stream()
                    .filter(event -> "VALIDATION".equals(event.eventType()))
                    .findFirst().orElse(events.get(0));
            DecisionMetricEvent finalEvent = events.get(events.size() - 1);
            boolean hadViolation = events.stream().anyMatch(event -> event.decision() == ValidationDecision.BLOCK);
            boolean hadRepair = events.stream().anyMatch(event -> event.eventType().startsWith("REPAIR_"));
            boolean needsReview = events.stream().anyMatch(IntentMetricsService::requiresReview);
            if (finalEvent.decision() == ValidationDecision.APPROVE) approved++;
            if (hadViolation) violating++;
            if (hadViolation && hadRepair && finalEvent.decision() == ValidationDecision.APPROVE) repaired++;
            if (needsReview) requiresReview++;

            BigDecimal originalAmount = firstValidation.calculatedTotal();
            if (originalAmount != null) {
                BigDecimal finalAmount = capturedByProposal.getOrDefault(firstValidation.proposalId(),
                        finalEvent.decision() == ValidationDecision.APPROVE && finalEvent.calculatedTotal() != null
                                ? finalEvent.calculatedTotal() : BigDecimal.ZERO);
                preventedAmount = preventedAmount.add(originalAmount.subtract(finalAmount).max(BigDecimal.ZERO));
            }
        }

        int total = proposals.size();
        return new IntentMetrics(total, approved, violating, repaired, requiresReview,
                preventedAmount, capturedAmount, rate(approved, total), rate(violating, total),
                rate(repaired, violating), rate(requiresReview, total));
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