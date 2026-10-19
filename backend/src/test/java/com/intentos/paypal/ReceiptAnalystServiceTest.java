package com.intentos.paypal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intentos.intent.IntentDecisionEventStore;
import com.intentos.intent.IntentDecisionEvent;
import com.intentos.intent.IntentLanguageModel;
import com.intentos.intent.IntentMetricsService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReceiptAnalystServiceTest {
    @Test
    void refusesToAskModelWhenNoReceiptEvidenceMatches() {
        AgentReceiptStore receiptStore = mock(AgentReceiptStore.class);
        IntentLanguageModel languageModel = mock(IntentLanguageModel.class);
        IntentMetricsService metrics = new IntentMetricsService(mock(IntentDecisionEventStore.class), receiptStore);
        when(receiptStore.search("INT-1", "what was captured", "", 10)).thenReturn(List.of());
        ReceiptAnalystService analyst = new ReceiptAnalystService(receiptStore, metrics,
            mock(IntentDecisionEventStore.class), languageModel,
            mock(ElasticAgentBuilderService.class), new ObjectMapper());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> analyst.answer("INT-1", "what was captured", ""));

        assertEquals(404, exception.getStatusCode().value());
        verify(languageModel, never()).generateAdvisory(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

        @Test
        void decisionOnlyBlockedRunCanBeExplainedWithEventCitation() {
        AgentReceiptStore receipts = mock(AgentReceiptStore.class);
        IntentDecisionEventStore events = mock(IntentDecisionEventStore.class);
        IntentLanguageModel languageModel = mock(IntentLanguageModel.class);
        IntentMetricsService metrics = new IntentMetricsService(events, receipts);
        when(receipts.search("INT-1", "why was the laptop blocked", "", 10)).thenReturn(List.of());
        when(receipts.searchMode()).thenReturn("LEXICAL");
        when(events.findAllByIntentId("INT-1")).thenReturn(List.of(new IntentDecisionEvent(
            "EVT-1", "INT-1", "PROP-1", java.time.Instant.now(), "SHOPPING_VALIDATION",
            com.intentos.validation.ValidationDecision.BLOCK, new java.math.BigDecimal("800"),
            List.of(new com.intentos.validation.PolicyViolation("BUDGET_EXCEEDED", "Laptop", "700", "800")),
            "Laptop exceeds budget", null, "RUN-1", "RUN-1-R1")));
        when(languageModel.generateAdvisory(org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(java.util.Optional.of("EVT-1 records a budget violation."));
        ReceiptAnalystService analyst = new ReceiptAnalystService(receipts, metrics, events, languageModel,
            mock(ElasticAgentBuilderService.class), new ObjectMapper().findAndRegisterModules());

        ReceiptAnalystResponse result = analyst.answer("INT-1", "why was the laptop blocked", "");

        assertEquals(List.of("EVT-1"), result.eventIds());
        assertEquals(List.of("RUN-1"), result.runIds());
        assertEquals("LEXICAL", result.retrievalMode());
        assertEquals("BEDROCK_GROUNDED_NARRATION", result.narrationMode());
        }
}