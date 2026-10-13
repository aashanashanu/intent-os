package com.intentos.paypal;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryPayPalCheckoutStoreTest {
    @Test
    void onlyOneCaptureCanStartAndCompletedResultIsRetainedForRetries() {
        InMemoryPayPalCheckoutStore store = new InMemoryPayPalCheckoutStore();
        store.save(new PayPalCheckoutSession("ORDER-1", "INT-1", "PROP-1",
            new com.intentos.intent.TransactionProposal("PROP-1", "INT-1", "USD", List.of(), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("48.00")),
            new BigDecimal("48.00"),
                "USD", PayPalCheckoutSession.State.CREATED, null));

        assertTrue(store.beginCapture("ORDER-1"));
        assertFalse(store.beginCapture("ORDER-1"));
        var capture = new PayPalCaptureResponse("ORDER-1", "COMPLETED", "CAPTURE-1", new BigDecimal("48.00"), "USD");
        store.finishCapture("ORDER-1", capture);

        assertEquals(PayPalCheckoutSession.State.CAPTURED, store.find("ORDER-1").orElseThrow().state());
        assertEquals(capture, store.find("ORDER-1").orElseThrow().capture());
    }
}