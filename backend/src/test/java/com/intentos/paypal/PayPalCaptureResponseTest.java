package com.intentos.paypal;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PayPalCaptureResponseTest {
    @Test
    void persistedCaptureMatchesWebhookWithDifferentDecimalScale() throws Exception {
        PayPalCaptureResponse persisted = new ObjectMapper().readValue("""
                {"orderId":"ORDER-1","status":"COMPLETED","captureId":"CAP-1",
                 "amount":854.4,"currency":"USD"}
                """, PayPalCaptureResponse.class);
        PayPalCaptureResponse webhook = capture("CAP-1", "854.40");
        InMemoryPayPalCheckoutStore store = new InMemoryPayPalCheckoutStore();
        store.save(new PayPalCheckoutSession("ORDER-1", "INT-1", "PROP-1", null,
                new BigDecimal("854.40"), "USD", PayPalCheckoutSession.State.CAPTURED, persisted));

        assertEquals(persisted, webhook);
        assertEquals(persisted.hashCode(), webhook.hashCode());
        assertDoesNotThrow(() -> store.recordWebhookCapture("ORDER-1", webhook));
        assertDoesNotThrow(() -> store.finishCapture("ORDER-1", webhook));
        assertThrows(IllegalStateException.class,
                () -> store.recordWebhookCapture("ORDER-1", capture("CAP-1", "854.41")));
        assertThrows(IllegalStateException.class,
                () -> store.recordWebhookCapture("ORDER-1", capture("CAP-OTHER", "854.40")));
    }

    private static PayPalCaptureResponse capture(String captureId, String amount) {
        return new PayPalCaptureResponse("ORDER-1", "COMPLETED", captureId, new BigDecimal(amount), "USD");
    }
}