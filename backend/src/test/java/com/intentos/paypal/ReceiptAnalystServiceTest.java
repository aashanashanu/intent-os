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
            mock(ElasticAgentBuilderService.class), new ObjectMapper(), mock(AnalystToolScopeService.class));

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
            mock(ElasticAgentBuilderService.class), new ObjectMapper().findAndRegisterModules(), mock(AnalystToolScopeService.class));

        ReceiptAnalystResponse result = analyst.answer("INT-1", "why was the laptop blocked", "");

        assertEquals(List.of("EVT-1"), result.eventIds());
        assertEquals(List.of("RUN-1"), result.runIds());
        assertEquals("LEXICAL", result.retrievalMode());
        assertEquals("BEDROCK_GROUNDED_NARRATION", result.narrationMode());
        }

        @Test
        void toolLessNarrationIsRejectedAndCapabilityRevoked() {
        var scopes = mock(AnalystToolScopeService.class);
        var builder = mock(ElasticAgentBuilderService.class);
        var intent = mock(com.intentos.intent.IntentContract.class);
        when(builder.isConfigured()).thenReturn(true);
        when(scopes.issue(intent)).thenReturn("private-capability");
        when(builder.answer(org.mockito.ArgumentMatchers.anyString())).thenReturn("Unverified explanation");
        when(scopes.completedCalls("private-capability")).thenReturn(List.of());
        var analyst = new ReceiptAnalystService(mock(AgentReceiptStore.class), mock(IntentMetricsService.class),
            mock(IntentDecisionEventStore.class), mock(IntentLanguageModel.class), builder, new ObjectMapper(), scopes);

        assertThrows(ResponseStatusException.class, () -> analyst.answer(intent, "why", ""));

        verify(scopes).revoke("private-capability");
        }

        @Test
        void successfulToolCallsSupplyCitationsAndRevokeCapability() {
        var scopes = mock(AnalystToolScopeService.class);
        var builder = mock(ElasticAgentBuilderService.class);
        var intent = mock(com.intentos.intent.IntentContract.class);
        var receipts = mock(AgentReceiptStore.class);
        var event = new IntentDecisionEvent("EVT-1", "INT-1", "PROP-1", java.time.Instant.now(),
            "SHOPPING_VALIDATION", com.intentos.validation.ValidationDecision.BLOCK,
            java.math.BigDecimal.TEN, List.of(), "Blocked proposal");
        when(builder.isConfigured()).thenReturn(true);
        when(scopes.issue(intent)).thenReturn("private-capability");
        when(builder.answer(org.mockito.ArgumentMatchers.anyString())).thenReturn("EVT-1 records a blocked proposal.");
        when(scopes.completedCalls("private-capability")).thenReturn(List.of("get_decisions", "get_metrics"));
        when(scopes.evidence("private-capability")).thenReturn(new AnalystToolScopeService.Evidence(List.of(), List.of(event)));
        when(receipts.searchMode()).thenReturn("LEXICAL");
        var analyst = new ReceiptAnalystService(receipts, mock(IntentMetricsService.class),
            mock(IntentDecisionEventStore.class), mock(IntentLanguageModel.class), builder, new ObjectMapper(), scopes);

        var result = analyst.answer(intent, "why", "");

        assertEquals("AGENT_BUILDER_SECURED_MCP", result.narrationMode());
        assertEquals(List.of("EVT-1"), result.eventIds());
        assertEquals(List.of("get_decisions", "get_metrics"), result.toolCalls());
        verify(scopes).revoke("private-capability");

        when(builder.answer(org.mockito.ArgumentMatchers.anyString())).thenReturn("EVT-1 and EVT-FOREIGN explain this.");
        assertThrows(ResponseStatusException.class, () -> analyst.answer(intent, "why", ""));
        when(builder.answer(org.mockito.ArgumentMatchers.anyString())).thenReturn("No citation supplied.");
        assertThrows(ResponseStatusException.class, () -> analyst.answer(intent, "why", ""));
        when(builder.answer(org.mockito.ArgumentMatchers.anyString())).thenReturn("EVT-1 private-capability");
        assertThrows(ResponseStatusException.class, () -> analyst.answer(intent, "why", ""));
        }
}