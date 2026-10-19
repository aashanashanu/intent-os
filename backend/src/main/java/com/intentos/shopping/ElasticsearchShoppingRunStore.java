package com.intentos.shopping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@ConditionalOnProperty(name = "intentos.elastic.enabled", havingValue = "true")
public class ElasticsearchShoppingRunStore implements ShoppingRunStore {
    private static final String RUN_INDEX = "shopping-run-v1";
    private static final String REVISION_INDEX = "shopping-revision-v1";
    private static final String REVIEW_INDEX = "shopping-review-v1";
    private final RestClient client;
    private final ObjectMapper objectMapper;

    public ElasticsearchShoppingRunStore(@Value("${intentos.elastic.url:}") String url,
            @Value("${intentos.elastic.api-key:}") String apiKey, ObjectMapper objectMapper) {
        if (url.isBlank() || apiKey.isBlank()) {
            throw new IllegalStateException("Elastic is enabled but ELASTIC_URL or ELASTIC_API_KEY is missing");
        }
        client = RestClient.builder().baseUrl(url)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "ApiKey " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public ShoppingRun createRun(ShoppingRun run) {
        client.put().uri("/{index}/_create/{id}", RUN_INDEX, run.runId())
                .body(run).retrieve().toBodilessEntity();
        return run;
    }

    @Override
    public Optional<ShoppingRun> findRun(String runId) {
        JsonNode response = client.get().uri("/{index}/_doc/{id}", RUN_INDEX, runId)
                .exchange((request, result) -> readResponse(result.getStatusCode().value(), result.getBody()));
        if (response == null || !response.path("found").asBoolean(false)) return Optional.empty();
        return Optional.of(decode(response.path("_source"), ShoppingRun.class));
    }

    @Override
    public List<ShoppingRun> findRunsByIntentId(String intentId) {
        JsonNode response = search(RUN_INDEX, "intentId", intentId);
        List<ShoppingRun> runs = new ArrayList<>();
        if (response != null) {
            for (JsonNode hit : response.path("hits").path("hits")) {
                runs.add(decode(hit.path("_source"), ShoppingRun.class));
            }
        }
        return runs.stream().sorted(Comparator.comparing(ShoppingRun::createdAt).reversed()).toList();
    }

    @Override
    public ShoppingRevision createRevision(ShoppingRevision revision) {
        client.put().uri("/{index}/_create/{id}", REVISION_INDEX, revision.revisionId())
                .body(revision).retrieve().toBodilessEntity();
        return revision;
    }

    @Override
    public List<ShoppingRevision> findRevisions(String runId) {
        JsonNode response = search(REVISION_INDEX, "runId", runId);
        List<ShoppingRevision> revisions = new ArrayList<>();
        if (response != null) {
            for (JsonNode hit : response.path("hits").path("hits")) {
                revisions.add(decode(hit.path("_source"), ShoppingRevision.class));
            }
        }
        return revisions.stream().sorted(Comparator.comparingInt(ShoppingRevision::revisionNumber)).toList();
    }

    @Override
    public ShoppingReview saveReview(ShoppingReview review) {
        client.put().uri("/{index}/_create/{id}", REVIEW_INDEX, review.reviewId())
                .body(review).retrieve().toBodilessEntity();
        return review;
    }

    @Override
    public Optional<ShoppingReview> findReviewForRevision(String runId, String revisionId, String userId) {
        JsonNode response = client.post().uri("/{index}/_search", REVIEW_INDEX)
                .body(Map.of("query", Map.of("bool", Map.of("filter", List.of(
                        Map.of("term", Map.of("runId.keyword", runId)),
                        Map.of("term", Map.of("revisionId.keyword", revisionId)),
                        Map.of("term", Map.of("userId.keyword", userId))))), "size", 1))
                .exchange((request, result) -> readResponse(result.getStatusCode().value(), result.getBody()));
        if (response == null || response.path("hits").path("hits").isEmpty()) return Optional.empty();
        return Optional.of(decode(response.path("hits").path("hits").get(0).path("_source"), ShoppingReview.class));
    }

    private JsonNode search(String index, String field, String value) {
        return client.post().uri("/{index}/_search", index)
                .body(Map.of("query", Map.of("term", Map.of(field + ".keyword", value)),
                        "sort", List.of(Map.of("createdAt", Map.of("order", "asc"))), "size", 500))
                .exchange((request, result) -> readResponse(result.getStatusCode().value(), result.getBody()));
    }

    private JsonNode readResponse(int status, java.io.InputStream body) {
        if (status == 404) return null;
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("Elastic shopping run request failed with HTTP " + status);
        }
        try {
            return objectMapper.readTree(body);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not decode Elastic shopping run response", exception);
        }
    }

    private <T> T decode(JsonNode source, Class<T> type) {
        try {
            return objectMapper.treeToValue(source, type);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not decode stored shopping lineage", exception);
        }
    }
}