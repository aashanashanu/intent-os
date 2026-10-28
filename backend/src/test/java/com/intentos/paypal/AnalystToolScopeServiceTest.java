package com.intentos.paypal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intentos.intent.IntentContract;
import com.intentos.intent.IntentDecisionEventStore;
import com.intentos.intent.IntentMetricsService;
import com.intentos.intent.IntentStore;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AnalystToolScopeServiceTest {
    private final IntentStore intents = mock(IntentStore.class);
    private final AgentReceiptStore receipts = mock(AgentReceiptStore.class);
    private final IntentDecisionEventStore events = mock(IntentDecisionEventStore.class);
    private final IntentMetricsService metrics = mock(IntentMetricsService.class);
    private final Clock clock = mock(Clock.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final AnalystToolScopeService service = new AnalystToolScopeService(intents, receipts, events, metrics, clock);

    private String capability() {
        when(clock.instant()).thenReturn(Instant.parse("2026-10-28T00:00:00Z"));
        IntentContract intent = mock(IntentContract.class);
        when(intent.intentId()).thenReturn("INT-OWN");
        when(intent.userId()).thenReturn("OWNER");
        when(intents.findByIdAndUserId("INT-OWN", "OWNER")).thenReturn(Optional.of(intent));
        return service.issue(intent);
    }

    @Test
    void executesOnlyWithinServerBoundScopeAndRecordsCompletedCalls() {
        String token = capability();
        when(events.findAllByIntentId("INT-OWN")).thenReturn(List.of());
        assertEquals(List.of(), service.execute("get_decisions", mapper.valueToTree(Map.of("capability", token))));
        assertEquals(List.of("get_decisions"), service.completedCalls(token));
        verify(events).findAllByIntentId("INT-OWN");
        service.revoke(token);
        assertThrows(ResponseStatusException.class,
                () -> service.execute("get_decisions", mapper.valueToTree(Map.of("capability", token))));
    }

    @Test
    void rejectsForgedExpiredAndScopeOverrideRequests() {
        String token = capability();
        assertThrows(ResponseStatusException.class, () -> service.execute("get_decisions",
                mapper.valueToTree(Map.of("capability", "forged"))));
        assertThrows(ResponseStatusException.class, () -> service.execute("get_decisions",
                mapper.valueToTree(Map.of("capability", token, "intentId", "FOREIGN"))));
        when(clock.instant()).thenReturn(Instant.parse("2026-10-28T00:03:00Z"));
        assertThrows(ResponseStatusException.class, () -> service.execute("get_decisions",
                mapper.valueToTree(Map.of("capability", token))));
        verifyNoInteractions(events, receipts, metrics);
    }

    @Test
    void rejectsWriteToolsAndExhaustedCapabilities() {
        String token = capability();
        when(events.findAllByIntentId("INT-OWN")).thenReturn(List.of());
        assertThrows(ResponseStatusException.class, () -> service.execute("capture_payment",
                mapper.valueToTree(Map.of("capability", token))));
        for (int attempt = 0; attempt < 8; attempt++) {
            service.execute("get_decisions", mapper.valueToTree(Map.of("capability", token)));
        }
        assertThrows(ResponseStatusException.class, () -> service.execute("get_decisions",
                mapper.valueToTree(Map.of("capability", token))));
    }

    @Test
    void foreignReceiptIdsAndRevokedOwnersCannotReadData() {
        String token = capability();
        when(receipts.findAllByIntentId("INT-OWN")).thenReturn(List.of());
        assertThrows(ResponseStatusException.class, () -> service.execute("get_receipt",
                mapper.valueToTree(Map.of("capability", token, "receiptId", "FOREIGN-RECEIPT"))));
        verify(receipts).findAllByIntentId("INT-OWN");
        when(intents.findByIdAndUserId("INT-OWN", "OWNER")).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> service.execute("get_metrics",
                mapper.valueToTree(Map.of("capability", token))));
        verifyNoInteractions(metrics);
    }
}