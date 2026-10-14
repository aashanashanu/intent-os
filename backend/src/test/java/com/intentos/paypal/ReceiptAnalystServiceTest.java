package com.intentos.paypal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intentos.intent.IntentDecisionEventStore;
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
        ReceiptAnalystService analyst = new ReceiptAnalystService(receiptStore, metrics, languageModel,
            mock(ElasticAgentBuilderService.class), new ObjectMapper());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> analyst.answer("INT-1", "what was captured", ""));

        assertEquals(404, exception.getStatusCode().value());
        verify(languageModel, never()).generateAdvisory(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }
}