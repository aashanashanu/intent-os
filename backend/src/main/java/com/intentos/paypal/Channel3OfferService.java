package com.intentos.paypal;

import com.fasterxml.jackson.databind.JsonNode;
import com.intentos.intent.IntentContract;
import com.intentos.intent.OfferEvidence;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
public class Channel3OfferService {
    private static final String API_URL = "https://api.trychannel3.com/v1";
    private final String apiKey;
    private final RestClient client;

    public Channel3OfferService(
            @Value("${channel3.api-key:}") String apiKey,
            RestClient.Builder clientBuilder) {
        this.apiKey = apiKey;
        this.client = clientBuilder.build();
    }

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    public List<Channel3Offer> search(IntentContract intent) {
        requireConfigured();
        if (intent.goal() == null || intent.goal().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Intent goal is required for product search");
        }
        Map<String, Object> filters = new HashMap<>();
        BigDecimal maximumPrice = effectiveBudget(intent);
        if (maximumPrice != null) filters.put("price", Map.of("max_price", maximumPrice));
        filters.put("availability", List.of("InStock"));
        String query = searchQuery(intent);
        Map<String, Object> body = Map.of("query", query, "limit", 20,
                "currency", intent.currency() == null ? "USD" : intent.currency(), "filters", filters);
        JsonNode response = client.post().uri(API_URL + "/search")
                .header("x-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        return parseOffers(response, Instant.now()).stream()
                .filter(offer -> "InStock".equalsIgnoreCase(offer.availability()))
                .filter(offer -> intent.currency() == null || intent.currency().equalsIgnoreCase(offer.currency()))
                .filter(offer -> maximumPrice == null || offer.price().compareTo(maximumPrice) <= 0)
                .toList();
    }

    public OfferEvidence refresh(OfferEvidence evidence) {
        requireConfigured();
        if (evidence == null || evidence.offerUrl() == null || evidence.offerUrl().isBlank()
            || evidence.productId() == null || evidence.productId().isBlank()
            || evidence.productName() == null || evidence.productName().isBlank()
            || evidence.retailer() == null || evidence.retailer().isBlank()
            || evidence.price() == null || evidence.currency() == null || evidence.currency().isBlank()
            || !"channel3".equalsIgnoreCase(evidence.source())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A Channel3 offer URL is required");
        }
        JsonNode response = client.post().uri(API_URL + "/lookup")
                .header("x-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(Map.of("url", evidence.offerUrl()))
                .retrieve()
                .body(JsonNode.class);
        List<Channel3Offer> offers = parseOffers(response, Instant.now());
        Channel3Offer match = offers.stream()
                .filter(offer -> evidence.offerUrl().equals(offer.offerUrl()))
                .filter(offer -> evidence.productId().equals(offer.productId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "The selected Channel3 offer is no longer available at that URL"));
        if (!"InStock".equalsIgnoreCase(match.availability())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The selected retailer offer is no longer in stock");
        }
        if (!evidence.currency().equalsIgnoreCase(match.currency())
                || evidence.price().compareTo(match.price()) != 0
                || !evidence.retailer().equalsIgnoreCase(match.retailer())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The selected offer price or retailer changed; review the refreshed offer before checkout");
        }
        return match.evidence();
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Channel3 API credentials are not configured");
        }
    }

    private static String searchQuery(IntentContract intent) {
        List<String> terms = new ArrayList<>();
        terms.add(intent.goal());
        intent.requiredAttributes().forEach((name, value) -> terms.add(name + " " + value));
        return String.join(" ", terms);
    }

    private static BigDecimal effectiveBudget(IntentContract intent) {
        BigDecimal maximum = intent.maxAmount();
        if (intent.maximumTransactionAmount() != null
                && (maximum == null || intent.maximumTransactionAmount().compareTo(maximum) < 0)) {
            maximum = intent.maximumTransactionAmount();
        }
        return maximum;
    }

    private static List<Channel3Offer> parseOffers(JsonNode response, Instant retrievedAt) {
        List<Channel3Offer> offers = new ArrayList<>();
        if (response == null) return List.of();
        List<JsonNode> products = new ArrayList<>();
        if (response.path("products").isArray()) response.path("products").forEach(products::add);
        else if (response.path("product").isObject()) products.add(response.path("product"));
        else if (response.path("id").isTextual()) products.add(response);
        for (JsonNode product : products) {
            String productId = product.path("id").asText("");
            String productName = product.path("title").asText("");
            String description = product.path("description").asText("");
            String category = categoryName(product.path("category"));
            Map<String, String> attributes = attributes(product.path("structured_attributes"));
            for (JsonNode offer : product.path("offers")) {
                String url = offer.path("url").asText("");
                String retailer = offer.path("domain").asText("");
                JsonNode price = offer.path("price");
                JsonNode amount = price.has("amount") ? price.path("amount") : price.path("price");
                String currency = price.path("currency").asText("");
                BigDecimal value;
                try {
                    value = new BigDecimal(amount.asText());
                } catch (NumberFormatException exception) {
                    continue;
                }
                if (productId.isBlank() || productName.isBlank() || url.isBlank()
                        || retailer.isBlank() || currency.isBlank() || value.signum() < 0) continue;
                offers.add(new Channel3Offer(productId, productName, description, category, attributes,
                        retailer, url, value, currency, offer.path("availability").asText("Unknown"),
                        offer.path("condition").asText("Unknown"), retrievedAt));
            }
        }
        return List.copyOf(offers);
    }

    private static String categoryName(JsonNode category) {
        if (category.isTextual()) return category.asText();
        return category.path("title").asText(category.path("slug").asText(""));
    }

    private static Map<String, String> attributes(JsonNode node) {
        Map<String, String> values = new HashMap<>();
        if (node.isObject()) node.fields().forEachRemaining(entry -> {
            if (!entry.getValue().isNull()) values.put(entry.getKey(), entry.getValue().asText());
        });
        return Map.copyOf(values);
    }
}