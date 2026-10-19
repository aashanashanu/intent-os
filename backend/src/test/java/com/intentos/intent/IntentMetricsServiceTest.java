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

    @Test
    void groupsRevisionsByRunAndCountsHoldViolationsWithoutInflatingPreventedSpend() {
        IntentDecisionEventStore events = mock(IntentDecisionEventStore.class);
        AgentReceiptStore receipts = mock(AgentReceiptStore.class);
        when(events.findMetricEventsByIntentId("INT-1")).thenReturn(List.of(
                new DecisionMetricEvent("PROP-1", "SHOPPING_VALIDATION", ValidationDecision.HOLD,
                        new BigDecimal("500"), Instant.parse("2026-10-13T00:00:01Z"), "Missing terms",
                        "RUN-1", "RUN-1-R1", 1),
                new DecisionMetricEvent("PROP-2", "REPAIR_COMPLETED", ValidationDecision.HOLD,
                        new BigDecimal("480"), Instant.parse("2026-10-13T00:00:02Z"), "User review required",
                        "RUN-1", "RUN-1-R2", 0)));
        when(receipts.findAllByIntentId("INT-1")).thenReturn(List.of());

        IntentMetrics result = new IntentMetricsService(events, receipts).calculate("INT-1");

        assertEquals(1, result.totalProposals());
        assertEquals(0, result.approvedProposals());
        assertEquals(1, result.violatingProposals());
        assertEquals(1, result.proposalsRequiringReview());
        assertEquals(1, result.blockedExposureProposals());
        assertEquals(0, result.preventedAmount().compareTo(BigDecimal.ZERO));
        assertEquals(0, result.blockedExposureAmount().compareTo(new BigDecimal("480")));
    }

    @Test
    void unresolvedBlockedProposalIsExposureNotPreventedSpend() {
        IntentDecisionEventStore events = mock(IntentDecisionEventStore.class);
        AgentReceiptStore receipts = mock(AgentReceiptStore.class);
        when(events.findMetricEventsByIntentId("INT-1")).thenReturn(List.of(
                new DecisionMetricEvent("PROP-1", "VALIDATION", ValidationDecision.BLOCK,
                        new BigDecimal("700"), Instant.parse("2026-10-13T00:00:01Z"), "Blocked",
                        null, null, 1)));
        when(receipts.findAllByIntentId("INT-1")).thenReturn(List.of());

        IntentMetrics result = new IntentMetricsService(events, receipts).calculate("INT-1");

        assertEquals(0, result.preventedAmount().compareTo(BigDecimal.ZERO));
        assertEquals(0, result.blockedExposureAmount().compareTo(new BigDecimal("700")));
    }

    private static DecisionMetricEvent event(String type, ValidationDecision decision, String total,
            int second, String proposalId) {
        return new DecisionMetricEvent(proposalId, type, decision, new BigDecimal(total),
                Instant.parse("2026-10-13T00:00:0" + second + "Z"), null);
    }
}