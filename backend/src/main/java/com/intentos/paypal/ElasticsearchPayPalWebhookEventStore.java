package com.intentos.paypal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;

@Service
@ConditionalOnProperty(name = "intentos.elastic.enabled", havingValue = "true")
public class ElasticsearchPayPalWebhookEventStore implements PayPalWebhookEventStore {
    private static final String INDEX = "paypal-webhook-v1";
    private final RestClient client;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public ElasticsearchPayPalWebhookEventStore(
            @Value("${intentos.elastic.url:}") String url,
            @Value("${intentos.elastic.api-key:}") String apiKey,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        if (url.isBlank() || apiKey.isBlank()) {
            throw new IllegalStateException("Elastic is enabled but ELASTIC_URL or ELASTIC_API_KEY is missing");
        }
        client = RestClient.builder().baseUrl(url)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "ApiKey " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isProcessed(String eventId) {
        JsonNode response = client.get().uri("/{index}/_doc/{id}", INDEX, eventId)
                .exchange((request, result) -> {
                    if (result.getStatusCode().value() == 404) return null;
                    if (!result.getStatusCode().is2xxSuccessful()) {
                        throw new IllegalStateException("Elastic webhook lookup failed with HTTP " + result.getStatusCode().value());
                    }
                    try {
                        return objectMapper.readTree(result.getBody());
                    } catch (IOException exception) {
                        throw new IllegalStateException("Could not decode webhook event status", exception);
                    }
                });
        return response != null && response.path("found").asBoolean(false);
    }

    @Override
    public boolean markProcessedIfAbsent(String eventId) {
        try {
            client.put().uri("/{index}/_create/{id}", INDEX, eventId)
                    .body(new ProcessedWebhook(eventId, true)).retrieve().toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.Conflict exception) {
            return false;
        }
    }

    private record ProcessedWebhook(String eventId, boolean processed) {}
}