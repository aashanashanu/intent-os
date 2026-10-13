package com.intentos.paypal;

import com.fasterxml.jackson.databind.JsonNode;
import com.intentos.intent.TransactionProposal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class PayPalOrdersService {
    private final String clientId;
    private final String clientSecret;
    private final String environment;
    private final String frontendUrl;
    private final String webhookId;
    private final RestClient client;
    private String cachedToken;
    private Instant tokenExpiry = Instant.EPOCH;

    public PayPalOrdersService(
            @Value("${paypal.client-id:}") String clientId,
            @Value("${paypal.client-secret:}") String clientSecret,
            @Value("${paypal.environment:sandbox}") String environment,
            @Value("${intentos.frontend-url:http://localhost:5173}") String frontendUrl,
            @Value("${paypal.webhook-id:}") String webhookId,
            RestClient.Builder clientBuilder) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.environment = environment;
        this.frontendUrl = frontendUrl.replaceAll("/$", "");
        this.webhookId = webhookId;
        this.client = clientBuilder.build();
    }

    public PayPalOrderResponse createOrder(
            String intentId, TransactionProposal proposal, BigDecimal calculatedTotal, String currency) {
        if (proposal.shipping() == null || proposal.tax() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Explicit tax and shipping amounts are required before PayPal checkout");
        }
        String accessToken = accessToken();
        BigDecimal itemTotal = proposal.items().stream()
            .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> amount = Map.of(
            "currency_code", currency,
            "value", money(calculatedTotal),
            "breakdown", Map.of(
                "item_total", Map.of("currency_code", currency, "value", money(itemTotal)),
                "shipping", Map.of("currency_code", currency, "value", money(proposal.shipping())),
                "tax_total", Map.of("currency_code", currency, "value", money(proposal.tax()))));
        List<Map<String, Object>> items = proposal.items().stream()
            .map(item -> Map.<String, Object>of(
                "name", item.name(),
                "quantity", Integer.toString(item.quantity()),
                "unit_amount", Map.of("currency_code", currency, "value", money(item.unitPrice()))))
            .toList();
        Map<String, Object> order = Map.of(
                "intent", "CAPTURE",
                "purchase_units", List.of(Map.of(
                        "reference_id", proposal.proposalId(),
                        "custom_id", intentId,
                "amount", amount,
                "items", items)),
                "application_context", Map.of(
                        "return_url", frontendUrl + "/?paypal=return",
                        "cancel_url", frontendUrl + "/?paypal=cancel"));
        String requestId = UUID.nameUUIDFromBytes((intentId + ":" + proposal.proposalId() + ":"
            + currency + ":" + calculatedTotal.stripTrailingZeros().toPlainString())
                .getBytes(StandardCharsets.UTF_8)).toString();
        JsonNode response = client.post()
                .uri(apiBaseUrl() + "/v2/checkout/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header("PayPal-Request-Id", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(order)
                .retrieve()
                .body(JsonNode.class);
        if (response == null || response.path("id").asText().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "PayPal did not return an order ID");
        }
        String approvalUrl = null;
        for (JsonNode link : response.path("links")) {
            if ("payer-action".equals(link.path("rel").asText()) || "approve".equals(link.path("rel").asText())) {
                approvalUrl = link.path("href").asText(null);
                break;
            }
        }
        if (approvalUrl == null || approvalUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "PayPal did not return a buyer approval link");
        }
        return new PayPalOrderResponse(response.path("id").asText(), response.path("status").asText(), approvalUrl);
    }

    public PayPalCaptureResponse captureOrder(PayPalCheckoutSession checkout) {
        String accessToken = accessToken();
        JsonNode order = client.get()
                .uri(apiBaseUrl() + "/v2/checkout/orders/{orderId}", checkout.orderId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(JsonNode.class);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "PayPal returned no order status");
        }
        if (!matchesCheckout(order, checkout)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "PayPal order does not match the approved checkout");
        }
        String orderStatus = order.path("status").asText();
        if ("COMPLETED".equals(orderStatus)) {
            return captureResponse(order, checkout);
        }
        if (!"APPROVED".equals(orderStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "PayPal buyer approval is not complete");
        }

        String requestId = UUID.nameUUIDFromBytes((checkout.orderId() + ":capture")
                .getBytes(StandardCharsets.UTF_8)).toString();
        JsonNode response = client.post()
                .uri(apiBaseUrl() + "/v2/checkout/orders/{orderId}/capture", checkout.orderId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header("PayPal-Request-Id", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(Map.of())
                .retrieve()
                .body(JsonNode.class);
        return captureResponse(response, checkout);
    }

        public boolean verifyWebhook(JsonNode event, String transmissionId, String transmissionTime,
            String transmissionSignature, String certificateUrl, String authAlgorithm) {
        if (webhookId.isBlank() || transmissionId == null || transmissionTime == null
            || transmissionSignature == null || certificateUrl == null || authAlgorithm == null) {
            return false;
        }
        JsonNode response = client.post()
            .uri(apiBaseUrl() + "/v1/notifications/verify-webhook-signature")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .body(Map.of(
                "auth_algo", authAlgorithm,
                "cert_url", certificateUrl,
                "transmission_id", transmissionId,
                "transmission_sig", transmissionSignature,
                "transmission_time", transmissionTime,
                "webhook_id", webhookId,
                "webhook_event", event))
            .retrieve()
            .body(JsonNode.class);
        return response != null && "SUCCESS".equals(response.path("verification_status").asText());
        }

    private static PayPalCaptureResponse captureResponse(JsonNode response, PayPalCheckoutSession checkout) {
        if (response == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "PayPal did not return capture evidence");
        }
        JsonNode capture = null;
        for (JsonNode unit : response.path("purchase_units")) {
            JsonNode captures = unit.path("payments").path("captures");
            if (captures.isArray() && !captures.isEmpty()) {
                capture = captures.get(0);
                break;
            }
        }
        if (capture == null || capture.path("id").asText().isBlank()
                || capture.path("status").asText().isBlank()
                || capture.path("amount").path("value").isMissingNode()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "PayPal did not return capture evidence");
        }
        BigDecimal capturedAmount;
        try {
            capturedAmount = new BigDecimal(capture.path("amount").path("value").asText());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "PayPal returned an invalid capture amount");
        }
        return new PayPalCaptureResponse(
                checkout.orderId(),
                capture.path("status").asText(),
                capture.path("id").asText(),
                capturedAmount,
                capture.path("amount").path("currency_code").asText());
    }

    private static boolean matchesCheckout(JsonNode order, PayPalCheckoutSession checkout) {
        for (JsonNode unit : order.path("purchase_units")) {
            JsonNode amount = unit.path("amount");
            BigDecimal orderAmount;
            try {
                orderAmount = new BigDecimal(amount.path("value").asText());
            } catch (NumberFormatException exception) {
                continue;
            }
            if (checkout.intentId().equals(unit.path("custom_id").asText())
                    && checkout.proposalId().equals(unit.path("reference_id").asText())
                    && checkout.currency().equals(amount.path("currency_code").asText())
                    && orderAmount.compareTo(checkout.amount()) == 0) {
                return true;
            }
        }
        return false;
    }

    private synchronized String accessToken() {
        if (clientId.isBlank() || clientSecret.isBlank()) {
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

    private static String money(BigDecimal amount) {
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "PayPal checkout amounts must not contain fractions smaller than one cent");
        }
    }

}