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
    private static final int METRICS_PAGE_SIZE = 500;
    private final RestClient client;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
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
        List<DecisionMetricEvent> events = new ArrayList<>();
        List<JsonNode> searchAfter = null;
        while (true) {
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("query", Map.of("term", Map.of("intentId.keyword", intentId)));
            body.put("sort", List.of(Map.of("occurredAt", Map.of("order", "asc")),
                Map.of("eventId.keyword", Map.of("order", "asc"))));
            body.put("size", METRICS_PAGE_SIZE);
            if (searchAfter != null) body.put("search_after", searchAfter);
            JsonNode response = client.post()
                    .uri("/{index}/_search", INDEX)
                .body(body)
                    .exchange((request, result) -> {
                        if (result.getStatusCode().value() == 404) return null;
                        if (!result.getStatusCode().is2xxSuccessful()) {
                            throw new IllegalStateException("Elastic event metrics search failed with HTTP "
                                    + result.getStatusCode().value());
                        }
                        try {
                            return objectMapper.readTree(result.getBody());
                        } catch (IOException exception) {
                            throw new IllegalStateException("Could not decode Elastic event metrics response", exception);
                        }
                    });
            if (response == null) break;
            JsonNode hits = response.path("hits").path("hits");
            for (JsonNode hit : hits) {
                JsonNode source = hit.path("_source");
                String decisionText = nullableText(source, "decision");
                com.intentos.validation.ValidationDecision decision = decisionText == null ? null
                        : com.intentos.validation.ValidationDecision.valueOf(decisionText.toUpperCase());
                events.add(new DecisionMetricEvent(
                        nullableText(source, "proposalId"), nullableText(source, "eventType"), decision,
                        decimal(source, "calculatedTotal"), instant(source, "occurredAt"),
                        nullableText(source, "message"), nullableText(source, "runId"),
                        nullableText(source, "revisionId"), source.path("violations").isArray()
                                ? source.path("violations").size() : 0));
            }
            if (hits.size() < METRICS_PAGE_SIZE) break;
            JsonNode lastSort = hits.get(hits.size() - 1).path("sort");
            if (!lastSort.isArray() || lastSort.isEmpty()) {
                throw new IllegalStateException("Elastic event metrics page did not include its search cursor");
            }
            searchAfter = new ArrayList<>();
            lastSort.forEach(searchAfter::add);
        }
        return List.copyOf(events);
    }

    private static String nullableText(JsonNode values, String key) {
        JsonNode value = values.path(key);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static java.math.BigDecimal decimal(JsonNode values, String key) {
        JsonNode value = values.path(key);
        if (value == null || value.isNull()) return null;
        try {
            return new java.math.BigDecimal(value.asText());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static java.time.Instant instant(JsonNode values, String key) {
        String value = nullableText(values, key);
        if (value == null) return null;
        try {
            return java.time.Instant.parse(value);
        } catch (java.time.format.DateTimeParseException exception) {
            return null;
        }
    }

}
