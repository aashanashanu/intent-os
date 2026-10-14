package com.intentos.paypal;

import com.intentos.intent.IntentContract;
import com.intentos.intent.IntentDecisionEvent;
import com.intentos.validation.PolicyViolation;
import com.intentos.validation.ValidationDecision;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InMemoryAgentReceiptStoreTest {
    @Test
    void searchesOnlyWithinIntentAndCanFilterFinalizedReceiptByHistoricalBlock() {
        InMemoryAgentReceiptStore store = new InMemoryAgentReceiptStore();
        store.saveIfAbsent(receipt("ORDER-1", "INT-1"));
        store.saveIfAbsent(receipt("ORDER-2", "INT-2"));

        var found = store.search("INT-1", "warranty", "BLOCK", 10);

        assertEquals(1, found.size());
        assertEquals("ORDER-1", found.get(0).paypalOrderId());
        assertEquals(List.of(), store.search("INT-1", "nonmatching phrase", "", 10));
    }

    private static AgentReceipt receipt(String orderId, String intentId) {
        IntentContract intent = new IntentContract(intentId, "USER-1", Instant.now(),
                "Buy a laptop without an extended warranty", "Laptop", new BigDecimal("1000"), "USD",
                Set.of(), Map.of(), Map.of(), Set.of(), false, false, false, null, Set.of(), Map.of(),
                null, BigDecimal.ONE, "BUYER_APPROVAL");
        PolicyViolation violation = new PolicyViolation("UNREQUESTED_ADDON", "Warranty", "not added", "added");
        IntentDecisionEvent event = new IntentDecisionEvent("EVENT-1", intentId, "PROP-1", Instant.now(),
                "VALIDATION", ValidationDecision.BLOCK, new BigDecimal("1100"), List.of(violation),
                "Blocked an unrequested warranty");
        return new AgentReceipt("RCPT-" + orderId, intentId, intent, List.of(), List.of(violation),
                List.of(), ValidationDecision.APPROVE, new BigDecimal("900"), new BigDecimal("200"), "USD", orderId,
                "CAPTURE-" + orderId, "COMPLETED", "COMPLETED", "WH-" + orderId, Instant.now(),
                List.of(event), "laptop warranty blocked");
    }
}
