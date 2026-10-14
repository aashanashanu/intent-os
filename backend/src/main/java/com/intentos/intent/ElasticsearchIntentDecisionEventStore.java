package com.intentos.intent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@ConditionalOnProperty(name = "intentos.elastic.enabled", havingValue = "true")
public class ElasticsearchIntentDecisionEventStore implements IntentDecisionEventStore {
    private static final String INDEX = "intent-event-v1";
    private final RestClient client;
    private final ObjectMapper objectMapper;

    public ElasticsearchIntentDecisionEventStore(
            @Value("${intentos.elastic.url:}") String url,
            @Value("${intentos.elastic.api-key:}") String apiKey,
            ObjectMapper objectMapper) {
        this(url, apiKey, objectMapper, RestClient.builder());
    }

    ElasticsearchIntentDecisionEventStore(String url, String apiKey, ObjectMapper objectMapper,
            RestClient.Builder clientBuilder) {
        if (url.isBlank() || apiKey.isBlank()) {
            throw new IllegalStateException("Elastic is enabled but ELASTIC_URL or ELASTIC_API_KEY is missing");
        }
        this.client = clientBuilder
                .baseUrl(url)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "ApiKey " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public IntentDecisionEvent append(IntentDecisionEvent event) {
        client.put()
                .uri("/{index}/_create/{id}", INDEX, event.eventId())
                .body(event)
                .retrieve()
                .toBodilessEntity();
        return event;
    }

    @Override
    public List<IntentDecisionEvent> findAllByIntentId(String intentId) {
        JsonNode response = client.post()
                .uri("/{index}/_search", INDEX)
                .body(Map.of(
                        "query", Map.of("term", Map.of("intentId.keyword", intentId)),
                        "sort", List.of(Map.of("occurredAt", Map.of("order", "asc"))),
                        "size", 500))
                .exchange((request, result) -> {
                    if (result.getStatusCode().value() == 404) return null;
                    if (!result.getStatusCode().is2xxSuccessful()) {
                        throw new IllegalStateException("Elastic event search failed with HTTP " + result.getStatusCode().value());
                    }
                    try {
                        return objectMapper.readTree(result.getBody());
                    } catch (IOException exception) {
                        throw new IllegalStateException("Could not decode Elastic event search response", exception);
                    }
                });
        List<IntentDecisionEvent> events = new ArrayList<>();
        if (response != null) {
            for (JsonNode hit : response.path("hits").path("hits")) {
                try {
                    events.add(objectMapper.treeToValue(hit.path("_source"), IntentDecisionEvent.class));
                } catch (IOException exception) {
                    throw new IllegalStateException("Could not decode stored intent decision event", exception);
                }
            }
        }
        return List.copyOf(events);
    }

    @Override
    public List<DecisionMetricEvent> findMetricEventsByIntentId(String intentId) {
        String escapedIntentId = intentId.replace("\\", "\\\\").replace("\"", "\\\"");
        JsonNode response = client.post().uri("/_query")
                .body(Map.of("query", "FROM " + INDEX
                    + " | WHERE intentId.keyword == \"" + escapedIntentId + "\""
                        + " | KEEP proposalId, eventType, decision, calculatedTotal, occurredAt, message"
                        + " | SORT occurredAt ASC | LIMIT 10000"))
                .exchange((request, result) -> {
                    if (result.getStatusCode().value() == 404) return null;
                    if (!result.getStatusCode().is2xxSuccessful()) {
                        throw new IllegalStateException("Elastic ES|QL query failed with HTTP " + result.getStatusCode().value());
                    }
                    try {
                        return objectMapper.readTree(result.getBody());
                    } catch (IOException exception) {
                        throw new IllegalStateException("Could not decode ES|QL analytics response", exception);
                    }
                });
        if (response == null) return List.of();
        List<String> columns = new ArrayList<>();
        response.path("columns").forEach(column -> columns.add(column.path("name").asText()));
        List<DecisionMetricEvent> events = new ArrayList<>();
        for (JsonNode row : response.path("values")) {
            Map<String, JsonNode> values = new java.util.HashMap<>();
            for (int index = 0; index < columns.size() && index < row.size(); index++) {
                values.put(columns.get(index), row.get(index));
            }
            String decisionText = text(values, "decision");
            com.intentos.validation.ValidationDecision decision = decisionText == null
                    ? null : com.intentos.validation.ValidationDecision.valueOf(decisionText.toUpperCase());
            java.math.BigDecimal total = decimal(values, "calculatedTotal");
            java.time.Instant occurredAt = instant(values, "occurredAt");
            events.add(new DecisionMetricEvent(text(values, "proposalId"), text(values, "eventType"),
                    decision, total, occurredAt, text(values, "message")));
        }
        return List.copyOf(events);
    }

    private static String text(Map<String, JsonNode> values, String key) {
        JsonNode value = values.get(key);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static java.math.BigDecimal decimal(Map<String, JsonNode> values, String key) {
        JsonNode value = values.get(key);
        if (value == null || value.isNull()) return null;
        try {
            return new java.math.BigDecimal(value.asText());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static java.time.Instant instant(Map<String, JsonNode> values, String key) {
        String value = text(values, key);
        if (value == null) return null;
        try {
            return java.time.Instant.parse(value);
        } catch (java.time.format.DateTimeParseException exception) {
            return null;
        }
    }
}
