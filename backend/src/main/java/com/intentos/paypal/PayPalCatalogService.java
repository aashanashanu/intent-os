package com.intentos.paypal;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class PayPalCatalogService {
    private final String clientId;
    private final String clientSecret;
    private final String environment;
    private final RestClient client;
    private String cachedToken;
    private Instant tokenExpiry = Instant.EPOCH;

    public PayPalCatalogService(
            @Value("${paypal.client-id:}") String clientId,
            @Value("${paypal.client-secret:}") String clientSecret,
            @Value("${paypal.environment:sandbox}") String environment) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.environment = environment;
        this.client = RestClient.create();
    }

    public JsonNode listProducts(int page, int pageSize) {
        String accessToken = accessToken();
        return client.get()
                .uri(apiBaseUrl() + "/v1/catalogs/products?page={page}&page_size={pageSize}", page, pageSize)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(JsonNode.class);
    }

    public boolean isConfigured() {
        return !clientId.isBlank() && !clientSecret.isBlank();
    }

    private synchronized String accessToken() {
        if (!isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "PayPal Sandbox credentials are not configured");
        }
        if (cachedToken != null && tokenExpiry.isAfter(Instant.now().plusSeconds(60))) return cachedToken;

        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        JsonNode tokenResponse = client.post()
                .uri(apiBaseUrl() + "/v1/oauth2/token")
                .headers(headers -> headers.setBasicAuth(clientId, clientSecret))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
        if (tokenResponse == null || tokenResponse.path("access_token").asText().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "PayPal did not return an access token");
        }
        cachedToken = tokenResponse.path("access_token").asText();
        tokenExpiry = Instant.now().plusSeconds(Math.max(60, tokenResponse.path("expires_in").asLong(300)));
        return cachedToken;
    }

    private String apiBaseUrl() {
        return "live".equalsIgnoreCase(environment)
                ? "https://api-m.paypal.com"
                : "https://api-m.sandbox.paypal.com";
    }
}