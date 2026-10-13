package com.intentos.paypal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@ConditionalOnProperty(name = "intentos.elastic.enabled", havingValue = "true")
public class ElasticsearchPayPalCheckoutStore implements PayPalCheckoutStore {
    private static final String INDEX = "paypal-checkout-v1";
    private final RestClient client;
    private final ObjectMapper objectMapper;

    public ElasticsearchPayPalCheckoutStore(
            @Value("${intentos.elastic.url:}") String url,
            @Value("${intentos.elastic.api-key:}") String apiKey,
            ObjectMapper objectMapper) {
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
    public void save(PayPalCheckoutSession session) {
        client.put().uri("/{index}/_doc/{id}", INDEX, session.orderId())
                .body(session).retrieve().toBodilessEntity();
    }

    @Override
    public Optional<PayPalCheckoutSession> find(String orderId) {
        JsonNode response = client.get().uri("/{index}/_doc/{id}", INDEX, orderId)
                .exchange((request, result) -> {
                    if (result.getStatusCode().value() == 404) return null;
                    if (!result.getStatusCode().is2xxSuccessful()) {
                        throw new IllegalStateException("Elastic checkout lookup failed with HTTP " + result.getStatusCode().value());
                    }
                    try {
                        return objectMapper.readTree(result.getBody());
                    } catch (IOException exception) {
                        throw new IllegalStateException("Could not decode checkout session", exception);
                    }
                });
        if (response == null || !response.path("found").asBoolean(false)) return Optional.empty();
        try {
            return Optional.of(objectMapper.treeToValue(response.path("_source"), PayPalCheckoutSession.class));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not decode stored checkout session", exception);
        }
    }

    @Override
    public synchronized boolean beginCapture(String orderId) {
        Optional<PayPalCheckoutSession> found = find(orderId);
        if (found.isEmpty() || found.get().state() != PayPalCheckoutSession.State.CREATED) return false;
        save(withState(found.get(), PayPalCheckoutSession.State.CAPTURING, null));
        return true;
    }

    @Override
    public synchronized void finishCapture(String orderId, PayPalCaptureResponse capture) {
        PayPalCheckoutSession session = find(orderId)
                .orElseThrow(() -> new IllegalStateException("PayPal checkout session was not found"));
        if (session.state() == PayPalCheckoutSession.State.CAPTURED
                && session.capture() != null && session.capture().equals(capture)) return;
        if (session.state() != PayPalCheckoutSession.State.CAPTURING) {
            throw new IllegalStateException("PayPal checkout is not being captured");
        }
        save(withState(session, stateFor(capture), capture));
    }

    @Override
    public synchronized void recordWebhookCapture(String orderId, PayPalCaptureResponse capture) {
        PayPalCheckoutSession session = find(orderId)
                .orElseThrow(() -> new IllegalStateException("PayPal checkout session was not found"));
        if (session.state() == PayPalCheckoutSession.State.CAPTURED && session.capture() != null) {
            if (session.capture().equals(capture)) return;
            throw new IllegalStateException("PayPal order has conflicting completed capture evidence");
        }
        save(withState(session, PayPalCheckoutSession.State.CAPTURED, capture));
    }

    @Override
    public synchronized void releaseCapture(String orderId) {
        find(orderId).filter(session -> session.state() == PayPalCheckoutSession.State.CAPTURING)
                .ifPresent(session -> save(withState(session, PayPalCheckoutSession.State.CREATED, null)));
    }

    private static PayPalCheckoutSession.State stateFor(PayPalCaptureResponse capture) {
        return switch (capture.status()) {
            case "COMPLETED" -> PayPalCheckoutSession.State.CAPTURED;
            case "PENDING" -> PayPalCheckoutSession.State.CAPTURE_PENDING;
            default -> PayPalCheckoutSession.State.CAPTURE_FAILED;
        };
    }

    private static PayPalCheckoutSession withState(
            PayPalCheckoutSession session, PayPalCheckoutSession.State state, PayPalCaptureResponse capture) {
        return new PayPalCheckoutSession(session.orderId(), session.intentId(), session.proposalId(),
                session.proposal(), session.amount(), session.currency(), state, capture);
    }
}