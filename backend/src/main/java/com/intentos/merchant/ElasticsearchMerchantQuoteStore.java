package com.intentos.merchant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@ConditionalOnProperty(name = "intentos.elastic.enabled", havingValue = "true")
public class ElasticsearchMerchantQuoteStore implements MerchantQuoteStore {
    private static final String INDEX = "merchant-quote-v1";
    private final RestClient client;
    private final ObjectMapper objectMapper;

    @Autowired
    public ElasticsearchMerchantQuoteStore(@Value("${intentos.elastic.url:}") String url,
            @Value("${intentos.elastic.api-key:}") String apiKey, ObjectMapper objectMapper) {
        this(url, apiKey, objectMapper, RestClient.builder());
    }

    ElasticsearchMerchantQuoteStore(String url, String apiKey, ObjectMapper objectMapper,
            RestClient.Builder builder) {
        if (url.isBlank() || apiKey.isBlank()) {
            throw new IllegalStateException("Elastic is enabled but ELASTIC_URL or ELASTIC_API_KEY is missing");
        }
        client = builder.baseUrl(url)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "ApiKey " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public MerchantQuote save(MerchantQuote quote) {
        client.put().uri("/{index}/_create/{id}", INDEX, quote.quoteId())
                .body(quote).retrieve().toBodilessEntity();
        return quote;
    }

    @Override
    public Optional<MerchantQuote> find(String quoteId) {
        JsonNode response = client.get().uri("/{index}/_doc/{id}", INDEX, quoteId)
                .exchange((request, result) -> {
                    if (result.getStatusCode().value() == 404) return null;
                    if (!result.getStatusCode().is2xxSuccessful()) {
                        throw new IllegalStateException("Elastic merchant quote lookup failed with HTTP "
                                + result.getStatusCode().value());
                    }
                    try {
                        return objectMapper.readTree(result.getBody());
                    } catch (IOException exception) {
                        throw new IllegalStateException("Could not decode stored merchant quote", exception);
                    }
                });
        if (response == null || !response.path("found").asBoolean(false)) return Optional.empty();
        try {
            return Optional.of(objectMapper.treeToValue(response.path("_source"), MerchantQuote.class));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not decode stored merchant quote", exception);
        }
    }
}