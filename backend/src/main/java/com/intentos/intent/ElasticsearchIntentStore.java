package com.intentos.intent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@ConditionalOnProperty(name = "intentos.elastic.enabled", havingValue = "true")
public class ElasticsearchIntentStore implements IntentStore {
    private static final String INDEX = "intent-v1";
    private final RestClient client;
    private final ObjectMapper objectMapper;

    public ElasticsearchIntentStore(
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
    public IntentContract save(IntentContract contract) {
        client.put()
                .uri("/{index}/_doc/{id}", INDEX, contract.intentId())
                .body(contract)
                .retrieve()
                .toBodilessEntity();
        return contract;
    }

    @Override
    public Optional<IntentContract> findById(String intentId) {
        JsonNode response = client.get()
                .uri("/{index}/_doc/{id}", INDEX, intentId)
                .exchange((request, result) -> {
                    if (result.getStatusCode().value() == 404) return null;
                    if (!result.getStatusCode().is2xxSuccessful()) {
                        throw new IllegalStateException("Elastic document lookup failed with HTTP " + result.getStatusCode().value());
                    }
                    try {
                        return objectMapper.readTree(result.getBody());
                    } catch (IOException exception) {
                        throw new IllegalStateException("Could not decode Elastic document", exception);
                    }
                });
        if (response == null || response.path("found").asBoolean(false) == false) return Optional.empty();
        return Optional.of(readContract(response.path("_source")));
    }

    @Override
    public Optional<IntentContract> findByIdAndUserId(String intentId, String userId) {
        return findById(intentId).filter(contract -> contract.userId().equals(userId));
    }

    @Override
    public List<IntentContract> findAll() {
        return search(Map.of("match_all", Map.of()));
    }

    @Override
    public List<IntentContract> findAllByUserId(String userId) {
        return search(Map.of("term", Map.of("userId.keyword", userId)));
    }

    private List<IntentContract> search(Map<String, Object> query) {
        JsonNode response = client.post()
                .uri("/{index}/_search", INDEX)
                .body(Map.of(
                        "query", query,
                        "sort", List.of(Map.of("createdAt", Map.of("order", "desc"))),
                        "size", 500))
                .exchange((request, result) -> {
                    if (result.getStatusCode().value() == 404) return null;
                    if (!result.getStatusCode().is2xxSuccessful()) {
                        throw new IllegalStateException("Elastic search failed with HTTP " + result.getStatusCode().value());
                    }
                    try {
                        return objectMapper.readTree(result.getBody());
                    } catch (IOException exception) {
                        throw new IllegalStateException("Could not decode Elastic search response", exception);
                    }
                });
        List<IntentContract> contracts = new ArrayList<>();
        if (response != null) {
            for (JsonNode hit : response.path("hits").path("hits")) {
                contracts.add(readContract(hit.path("_source")));
            }
        }
        return List.copyOf(contracts);
    }

    private IntentContract readContract(JsonNode source) {
        try {
            return objectMapper.treeToValue(source, IntentContract.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not decode stored Intent Contract", exception);
        }
    }
}