package com.intentos.paypal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class ElasticAgentBuilderService {
    private final String kibanaUrl;
    private final String apiKey;
    private final String agentId;
    private final RestClient client;
    private final ObjectMapper objectMapper;

    public ElasticAgentBuilderService(
            @Value("${intentos.elastic.kibana-url:}") String kibanaUrl,
            @Value("${intentos.elastic.agent-builder.api-key:}") String apiKey,
            @Value("${intentos.elastic.agent-builder.agent-id:receipt-analyst}") String agentId,
            RestClient.Builder clientBuilder,
            ObjectMapper objectMapper) {
        this.kibanaUrl = kibanaUrl == null ? "" : kibanaUrl.replaceAll("/$", "");
        this.apiKey = apiKey == null ? "" : apiKey;
        this.agentId = agentId;
        this.client = this.kibanaUrl.isBlank() ? null : clientBuilder.baseUrl(this.kibanaUrl).build();
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        return !kibanaUrl.isBlank() && !apiKey.isBlank();
    }

    public String answer(String groundedEvidence) {
        if (!isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Elastic Agent Builder is not configured");
        }
        JsonNode response;
        try {
            response = client.post().uri("/api/agent_builder/converse")
                    .header(HttpHeaders.AUTHORIZATION, "ApiKey " + apiKey)
                    .header("kbn-xsrf", "true")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(Map.of("agent_id", agentId, "input", groundedEvidence))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Elastic Agent Builder request failed", exception);
        }
        String text = responseText(response);
        if (text == null || text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Elastic Agent Builder returned no answer text");
        }
        return text;
    }

    private String responseText(JsonNode response) {
        if (response == null) return null;
        for (String field : List.of("response", "message", "text", "content", "rounds", "messages")) {
            JsonNode candidate = response.get(field);
            String text = extractText(candidate);
            if (text != null && !text.isBlank()) return text;
        }
        return null;
    }

    private static String extractText(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isTextual()) return node.asText();
        if (node.isArray()) {
            for (int index = node.size() - 1; index >= 0; index--) {
                String text = extractText(node.get(index));
                if (text != null && !text.isBlank()) return text;
            }
            return null;
        }
        if (node.isObject()) {
            for (String field : List.of("message", "text", "content", "response")) {
                String text = extractText(node.get(field));
                if (text != null && !text.isBlank()) return text;
            }
        }
        return null;
    }
}