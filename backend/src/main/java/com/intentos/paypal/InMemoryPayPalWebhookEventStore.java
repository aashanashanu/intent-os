package com.intentos.paypal;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "intentos.elastic.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryPayPalWebhookEventStore implements PayPalWebhookEventStore {
    private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();

    @Override
    public boolean isProcessed(String eventId) {
        return processedEventIds.contains(eventId);
    }

    @Override
    public boolean markProcessedIfAbsent(String eventId) {
        return processedEventIds.add(eventId);
    }
}