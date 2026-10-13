package com.intentos.paypal;

public interface PayPalWebhookEventStore {
    boolean isProcessed(String eventId);

    boolean markProcessedIfAbsent(String eventId);
}