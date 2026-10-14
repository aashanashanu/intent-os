package com.intentos.intent;

import com.intentos.paypal.AgentReceiptStore;
import com.intentos.validation.ValidationDecision;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IntentMetricsServiceTest {
    @Test
    void computesRepairedPreventionAndProposalRatesFromDecisionEvents() {
        IntentDecisionEventStore events = mock(IntentDecisionEventStore.class);
        AgentReceiptStore receipts = mock(AgentReceiptStore.class);
        when(events.findMetricEventsByIntentId("INT-1")).thenReturn(List.of(
                event("VALIDATION", ValidationDecision.BLOCK, "1249", 1, "PROP-1"),
                event("REPAIR_ATTEMPT", ValidationDecision.BLOCK, "1249", 2, "PROP-1"),
                event("VALIDATION", ValidationDecision.APPROVE, "971", 3, "PROP-1"),
                event("REPAIR_COMPLETED", ValidationDecision.APPROVE, "971", 4, "PROP-1")));
        when(receipts.findAllByIntentId("INT-1")).thenReturn(List.of());

        IntentMetrics result = new IntentMetricsService(events, receipts).calculate("INT-1");

        assertEquals(1, result.totalProposals());
        assertEquals(1, result.approvedProposals());
        assertEquals(1, result.violatingProposals());
        assertEquals(1, result.repairedProposals());
        assertEquals(0, result.proposalsRequiringReview());
        assertEquals(0, new BigDecimal("278").compareTo(result.preventedAmount()));
        assertEquals(0, result.intentComplianceRate().compareTo(BigDecimal.ONE));
        assertEquals(0, result.violationRate().compareTo(BigDecimal.ONE));
        assertEquals(0, result.repairRate().compareTo(BigDecimal.ONE));
    }

    @Test
    void returnsZeroRatesWhenThereAreNoDecisionEvents() {
        IntentDecisionEventStore events = mock(IntentDecisionEventStore.class);
        AgentReceiptStore receipts = mock(AgentReceiptStore.class);
        when(events.findMetricEventsByIntentId("INT-1")).thenReturn(List.of());
        when(receipts.findAllByIntentId("INT-1")).thenReturn(List.of());

        IntentMetrics result = new IntentMetricsService(events, receipts).calculate("INT-1");

        assertEquals(0, result.totalProposals());
        assertEquals(0, result.intentComplianceRate().compareTo(BigDecimal.ZERO));
        assertEquals(0, result.preventedAmount().compareTo(BigDecimal.ZERO));
    }

    private static DecisionMetricEvent event(String type, ValidationDecision decision, String total,
            int second, String proposalId) {
        return new DecisionMetricEvent(proposalId, type, decision, new BigDecimal(total),
                Instant.parse("2026-10-13T00:00:0" + second + "Z"), null);
    }
}