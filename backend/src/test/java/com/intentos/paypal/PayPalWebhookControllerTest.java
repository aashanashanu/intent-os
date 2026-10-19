package com.intentos.paypal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intentos.intent.IntentContract;
import com.intentos.intent.IntentDecisionEventStore;
import com.intentos.intent.IntentDecisionEvent;
import com.intentos.intent.IntentStore;
import com.intentos.intent.TransactionProposal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayPalWebhookControllerTest {
    @Mock private PayPalOrdersService ordersService;
    @Mock private PayPalCheckoutStore checkoutStore;
    @Mock private PayPalWebhookEventStore webhookEventStore;
    @Mock private AgentReceiptStore receiptStore;
    @Mock private IntentStore intentStore;
    @Mock private IntentDecisionEventStore decisionEventStore;

    private PayPalWebhookController controller;

    @BeforeEach
    void setUp() {
        controller = new PayPalWebhookController(ordersService, checkoutStore, webhookEventStore,
                receiptStore, intentStore, decisionEventStore);
    }

    @Test
    void rejectsInvalidWebhookBeforeReadingOrWritingPaymentState() throws Exception {
        when(ordersService.verifyWebhook(any(), any(), any(), any(), any(), any())).thenReturn(false);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.receive(event(), "transmission", "time", "signature", "cert", "algo"));

        assertEquals(401, exception.getStatusCode().value());
        verify(checkoutStore, never()).find(any());
        verify(receiptStore, never()).saveIfAbsent(any());
    }

    @Test
    void verifiedDuplicateWebhookCreatesOnlyOneReceiptAndTimelineEvent() throws Exception {
        when(ordersService.verifyWebhook(any(), any(), any(), any(), any(), any())).thenReturn(true);
        when(webhookEventStore.isProcessed("WH-1")).thenReturn(false, true);
        when(checkoutStore.find("ORDER-1")).thenReturn(Optional.of(checkout()));
        when(intentStore.findById("INT-1")).thenReturn(Optional.of(intent()));
        when(decisionEventStore.findAllByIntentId("INT-1")).thenReturn(List.of());
        when(receiptStore.saveIfAbsent(any(AgentReceipt.class))).thenReturn(true);

        controller.receive(event(), "transmission", "time", "signature", "cert", "algo");
        controller.receive(event(), "transmission", "time", "signature", "cert", "algo");

        verify(receiptStore, times(1)).saveIfAbsent(any(AgentReceipt.class));
        verify(decisionEventStore, times(1)).append(any());
        verify(webhookEventStore, times(1)).markProcessedIfAbsent("WH-1");
        verify(checkoutStore, times(1)).recordWebhookCapture(any(), any());
    }

        @Test
        void capturedReceiptExcludesOtherProposalEventsAndAttribution() throws Exception {
        when(ordersService.verifyWebhook(any(), any(), any(), any(), any(), any())).thenReturn(true);
        when(webhookEventStore.isProcessed("WH-1")).thenReturn(false);
        when(checkoutStore.find("ORDER-1")).thenReturn(Optional.of(checkout()));
        when(intentStore.findById("INT-1")).thenReturn(Optional.of(intent()));
        var unrelatedViolation = new com.intentos.validation.PolicyViolation(
            "UNREQUESTED_ADDON", "Warranty", "not requested", "added");
        when(decisionEventStore.findAllByIntentId("INT-1")).thenReturn(List.of(
            new IntentDecisionEvent("EVT-OTHER", "INT-1", "PROP-OTHER", Instant.now(), "VALIDATION",
                com.intentos.validation.ValidationDecision.BLOCK, new BigDecimal("900"),
                List.of(unrelatedViolation), "Different shopping run"),
            new IntentDecisionEvent("EVT-CURRENT", "INT-1", "PROP-1", Instant.now(), "VALIDATION",
                com.intentos.validation.ValidationDecision.BLOCK, new BigDecimal("60"), List.of(),
                "Captured proposal required repair")));
        when(receiptStore.saveIfAbsent(any(AgentReceipt.class))).thenReturn(true);

        controller.receive(event(), "transmission", "time", "signature", "cert", "algo");

        ArgumentCaptor<AgentReceipt> receipt = ArgumentCaptor.forClass(AgentReceipt.class);
        verify(receiptStore).saveIfAbsent(receipt.capture());
        assertEquals(0, new BigDecimal("12").compareTo(receipt.getValue().preventedAmount()));
        assertTrue(receipt.getValue().violations().isEmpty());
        assertEquals(2, receipt.getValue().activityTimeline().size());
        }

    private static com.fasterxml.jackson.databind.JsonNode event() throws Exception {
        return new ObjectMapper().readTree("""
                {
                  "id":"WH-1",
                  "event_type":"PAYMENT.CAPTURE.COMPLETED",
                  "resource":{
                    "id":"CAP-1",
                    "status":"COMPLETED",
                    "amount":{"currency_code":"USD","value":"48.00"},
                    "supplementary_data":{"related_ids":{"order_id":"ORDER-1"}}
                  }
                }
                """);
    }

    private static PayPalCheckoutSession checkout() {
        TransactionProposal proposal = new TransactionProposal("PROP-1", "INT-1", "USD", List.of(),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("48.00"));
        return new PayPalCheckoutSession("ORDER-1", "INT-1", "PROP-1", proposal, new BigDecimal("48.00"),
                "USD", PayPalCheckoutSession.State.CREATED, null);
    }

    private static IntentContract intent() {
        return new IntentContract("INT-1", "USER-1", Instant.now(), "Buy laptop", "Laptop",
                new BigDecimal("100.00"), "USD", java.util.Set.of(), Map.of(), Map.of(), java.util.Set.of(),
                false, false, false, null, java.util.Set.of(), Map.of(), null, BigDecimal.ONE, "BUYER_APPROVAL");
    }
}