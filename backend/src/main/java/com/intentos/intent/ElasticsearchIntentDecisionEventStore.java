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
        if (url.isBlank() || apiKey.isBlank()) {
            throw new IllegalStateException("Elastic is enabled but ELASTIC_URL or ELASTIC_API_KEY is missing");
        }
        this.client = RestClient.builder()
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
}
