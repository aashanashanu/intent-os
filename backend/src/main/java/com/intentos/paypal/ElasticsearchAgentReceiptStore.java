package com.intentos.paypal;

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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

@Service
@ConditionalOnProperty(name = "intentos.elastic.enabled", havingValue = "true")
public class ElasticsearchAgentReceiptStore implements AgentReceiptStore {
    private static final String INDEX = "agent-receipt-v1";
    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final String semanticField;
    private volatile boolean mappingInitialized;
    private volatile boolean semanticEnabled;
    private volatile boolean semanticDegraded;

    @org.springframework.beans.factory.annotation.Autowired
    public ElasticsearchAgentReceiptStore(
            @Value("${intentos.elastic.url:}") String url,
            @Value("${intentos.elastic.api-key:}") String apiKey,
            @Value("${intentos.elastic.semantic-field:searchText}") String semanticField,
            ObjectMapper objectMapper) {
        this(url, apiKey, semanticField, objectMapper, RestClient.builder());
    }

    ElasticsearchAgentReceiptStore(String url, String apiKey, String semanticField,
            ObjectMapper objectMapper, RestClient.Builder clientBuilder) {
        if (url.isBlank() || apiKey.isBlank()) {
            throw new IllegalStateException("Elastic is enabled but ELASTIC_URL or ELASTIC_API_KEY is missing");
        }
        client = clientBuilder.baseUrl(url)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "ApiKey " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.objectMapper = objectMapper;
        this.semanticField = semanticField;
    }

    @Override
    public boolean saveIfAbsent(AgentReceipt receipt) {
        ensureSearchMapping();
        try {
            client.put().uri("/{index}/_create/{id}", INDEX, receipt.paypalOrderId())
                    .body(receipt).retrieve().toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.Conflict exception) {
            return false;
        }
    }

    @Override
    public List<AgentReceipt> findAllByIntentId(String intentId) {
        return search(intentId, "", "", 500);
    }

    @Override
    public List<AgentReceipt> search(String intentId, String query, String status, int limit) {
        ensureSearchMapping();
        JsonNode response;
        try {
            response = executeSearch(intentId, query, status, limit, true);
        } catch (HttpClientErrorException exception) {
            if (query == null || query.isBlank() || exception.getStatusCode().value() != 400) throw exception;
            semanticDegraded = semanticEnabled;
            response = executeSearch(intentId, query, status, limit, false);
        }
        List<AgentReceipt> receipts = new ArrayList<>();
        if (response != null) {
            for (JsonNode hit : response.path("hits").path("hits")) {
                try {
                    receipts.add(objectMapper.treeToValue(hit.path("_source"), AgentReceipt.class));
                } catch (IOException exception) {
                    throw new IllegalStateException("Could not decode stored AgentReceipt", exception);
                }
            }
        }
        return List.copyOf(receipts);
    }

    private JsonNode executeSearch(String intentId, String query, String status, int limit, boolean hybrid) {
        Map<String, Object> bool = new java.util.LinkedHashMap<>();
        List<Object> filters = new ArrayList<>();
        filters.add(Map.of("term", Map.of("intentId.keyword", intentId)));
        if (status != null && !status.isBlank()) {
            String normalizedStatus = status.trim().toUpperCase(java.util.Locale.ROOT);
            filters.add(Map.of("bool", Map.of("should", List.of(
                    Map.of("term", Map.of("captureStatus.keyword", normalizedStatus)),
                    Map.of("term", Map.of("finalDecision.keyword", normalizedStatus)),
                    Map.of("term", Map.of("activityTimeline.decision.keyword", normalizedStatus)),
                    Map.of("term", Map.of("violations.type.keyword", normalizedStatus))),
                    "minimum_should_match", 1)));
        }
        bool.put("filter", filters);
        if (query != null && !query.isBlank()) {
            List<Object> should = new ArrayList<>();
            should.add(Map.of("multi_match", Map.of(
                    "query", query,
                    "type", "best_fields",
                    "fields", List.of("searchText^3", "intent.goal^2", "intent.naturalLanguageRequest^2",
                            "proposalHistory.items.name", "violations.type", "activityTimeline.message",
                            "paypalOrderId", "paypalCaptureId"))));
            if (hybrid && semanticEnabled && "searchText".equals(semanticField)) {
                should.add(Map.of("semantic", Map.of("field", semanticField, "query", query)));
            }
            bool.put("should", should);
            bool.put("minimum_should_match", 1);
        }
        try {
            List<Object> sort = query == null || query.isBlank()
                ? List.of(Map.of("createdAt", Map.of("order", "desc", "unmapped_type", "date")))
                : List.of(Map.of("_score", Map.of("order", "desc")),
                    Map.of("createdAt", Map.of("order", "desc", "unmapped_type", "date")));
            return client.post().uri("/{index}/_search", INDEX)
                    .body(Map.of("query", Map.of("bool", bool),
                    "sort", sort,
                            "size", Math.max(1, Math.min(100, limit))))
                    .retrieve().body(JsonNode.class);
        } catch (HttpClientErrorException.NotFound exception) {
            return null;
        }
    }

    @Override
    public String searchMode() {
        if (semanticDegraded) return "LEXICAL_DEGRADED";
        return semanticEnabled ? "HYBRID" : "LEXICAL";
    }

    private synchronized void ensureSearchMapping() {
        if (mappingInitialized) return;
        Map<String, Object> semanticProperties = Map.of(
            "searchText", Map.of("type", "semantic_text"), "createdAt", Map.of("type", "date"));
        int createStatus = requestStatus("/{index}", Map.of("mappings", Map.of("properties", semanticProperties)));
        if (createStatus >= 200 && createStatus < 300) {
            semanticEnabled = true;
            mappingInitialized = true;
            return;
        }
        int updateStatus = requestStatus("/{index}/_mapping", Map.of("properties", semanticProperties));
        if (updateStatus >= 200 && updateStatus < 300) {
            semanticEnabled = true;
            mappingInitialized = true;
            return;
        }
        requestStatus("/{index}", Map.of("mappings", Map.of("properties", Map.of(
            "searchText", Map.of("type", "text"), "createdAt", Map.of("type", "date")))));
        semanticEnabled = false;
        mappingInitialized = true;
    }

    private int requestStatus(String uri, Map<String, Object> body) {
        try {
            return client.put().uri(uri, INDEX).body(body)
                    .exchange((request, response) -> response.getStatusCode().value());
        } catch (RuntimeException exception) {
            return 500;
        }
    }
}