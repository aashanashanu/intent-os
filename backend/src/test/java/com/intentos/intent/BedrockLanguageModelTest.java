package com.intentos.intent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;

class BedrockLanguageModelTest {
    private final BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
    private final BedrockLanguageModel model = new BedrockLanguageModel(
            client, new ObjectMapper(), "amazon.nova-micro-v1:0", "amazon.nova-lite-v1:0",
            "amazon.nova-lite-v1:0", "amazon.nova-lite-v1:0", "amazon.nova-micro-v1:0", 700);

    @Test
    void extractionUsesConfiguredMicroModelAndAddsRequiredClarification() {
        when(client.converse(any(ConverseRequest.class)))
                .thenReturn(response("""
                        {"goal":"Development laptop","maxAmount":null,"currency":"USD","allowedCategories":["laptop"],"requiredAttributes":{"ram":"16GB"},"allowRecurringPayments":false,"allowSubscriptions":false,"allowUnrequestedAddons":false,"confidence":0.91,"clarificationQuestions":[]}
                        """))
                .thenReturn(response("{\"clarificationQuestions\":[],\"conflicts\":[]}"));

        IntentExtractionResult result = model.extract("I need a development laptop with 16GB RAM. No subscriptions.");

        assertEquals("Development laptop", result.goal());
        assertEquals("16GB", result.requiredAttributes().get("ram"));
        assertTrue(result.needsClarification());
        assertTrue(result.clarificationQuestions().stream().anyMatch(question -> question.contains("budget")));
        assertFalse(result.allowSubscriptions());
        verify(client).converse(org.mockito.ArgumentMatchers.argThat((ConverseRequest request) ->
                request.modelId().equals("amazon.nova-micro-v1:0")));
        verify(client).converse(org.mockito.ArgumentMatchers.argThat((ConverseRequest request) ->
                request.modelId().equals("amazon.nova-lite-v1:0")));
    }

    @Test
    void advisoryTaskUsesItsOwnConfiguredLiteModel() {
        when(client.converse(any(ConverseRequest.class))).thenReturn(response("Keep the subscription removed."));

        String suggestion = model.generateAdvisory(IntentModelTask.REPAIR, "Suggest safe changes only.", "Remove the unwanted subscription.").orElseThrow();

        assertEquals("Keep the subscription removed.", suggestion);
        verify(client).converse(org.mockito.ArgumentMatchers.argThat((ConverseRequest request) ->
                request.modelId().equals("amazon.nova-lite-v1:0")));
    }

    @Test
    void shoppingSelectionAcceptsOnlyIdsFromBoundedMerchantCandidates() {
        var intent = new IntentContract("intent-1", "user-1", java.time.Instant.now(), "Buy a laptop", "Laptop",
                new BigDecimal("1000"), "USD", java.util.Set.of("laptop"), Map.of(), Map.of(), java.util.Set.of(),
                false, false, false, null, java.util.Set.of(), Map.of(), null, BigDecimal.ONE, "BUYER_APPROVAL");
        var candidates = List.of(new ShoppingCandidate("portable-computer:16gb-512gb", "portable-computer",
                "16gb-512gb", "Portable Computer", "laptop", Map.of("ram", "16GB"),
                new BigDecimal("780.00"), 12, 2, "ONE_TIME", null, true));
        when(client.converse(any(ConverseRequest.class))).thenReturn(response(
                "{\"items\":[{\"candidateId\":\"portable-computer:16gb-512gb\",\"quantity\":1}],\"rationale\":\"Matches the laptop request\"}"));

        var selection = model.selectShopping(intent, candidates).orElseThrow();

        assertEquals(1, selection.items().size());
        assertEquals("portable-computer:16gb-512gb", selection.items().getFirst().candidateId());
        verify(client).converse(org.mockito.ArgumentMatchers.argThat((ConverseRequest request) ->
                request.modelId().equals("amazon.nova-lite-v1:0")));
    }

    @Test
    void shoppingSelectionAcceptsFencedJsonWithHarmlessExtraFields() {
        var intent = new IntentContract("intent-1", "user-1", java.time.Instant.now(), "Buy a laptop", "Laptop",
                new BigDecimal("1000"), "USD", java.util.Set.of(), Map.of(), Map.of(), java.util.Set.of(),
                false, false, false, null, java.util.Set.of(), Map.of(), null, BigDecimal.ONE, "BUYER_APPROVAL");
        var candidates = List.of(new ShoppingCandidate("portable-computer:16gb-512gb", "portable-computer",
                "16gb-512gb", "Portable Computer", "laptop", Map.of("ram", "16GB"),
                new BigDecimal("780.00"), 12, 2, "ONE_TIME", null, true));
        when(client.converse(any(ConverseRequest.class))).thenReturn(response("""
                ```json
                {"items":[{"candidateId":"portable-computer:16gb-512gb","quantity":1,"reason":"fits"}],
                 "rationale":"Fits the supplied intent","confidence":0.93}
                ```
                """));

        var selection = model.selectShopping(intent, candidates).orElseThrow();

        assertEquals("portable-computer:16gb-512gb", selection.items().getFirst().candidateId());
    }

    @Test
    void shoppingSelectionRejectsInventedCandidateIds() {
        var intent = new IntentContract("intent-1", "user-1", java.time.Instant.now(), "Buy a laptop", "Laptop",
                new BigDecimal("1000"), "USD", java.util.Set.of(), Map.of(), Map.of(), java.util.Set.of(),
                false, false, false, null, java.util.Set.of(), Map.of(), null, BigDecimal.ONE, "BUYER_APPROVAL");
        when(client.converse(any(ConverseRequest.class))).thenReturn(response(
                "{\"items\":[{\"candidateId\":\"made-up\",\"quantity\":1}],\"rationale\":\"\"}"));

        ResponseStatusException exception = org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException.class,
                () -> model.selectShopping(intent, List.of()));

        assertEquals(502, exception.getStatusCode().value());
        assertTrue(exception.getReason().contains("invalid shopping selection"));
    }

    @Test
    void preservesClarificationAndConfidenceForAmbiguousRequests() {
        when(client.converse(any(ConverseRequest.class)))
                .thenReturn(response("""
                        {"goal":null,"maxAmount":null,"currency":"USD","allowedCategories":[],"requiredAttributes":{},"allowRecurringPayments":false,"allowSubscriptions":false,"allowUnrequestedAddons":false,"confidence":0.2,"clarificationQuestions":["Which product category do you mean?"]}
                        """))
                .thenReturn(response("{\"clarificationQuestions\":[],\"conflicts\":[]}"));

        IntentExtractionResult result = model.extract("I need something for my setup.");

        assertTrue(result.needsClarification());
        assertEquals(2, result.clarificationQuestions().size());
        assertEquals(new BigDecimal("0.2"), result.confidence());
    }

    @Test
    void rejectsIncompleteModelJsonWithoutFallback() {
        when(client.converse(any(ConverseRequest.class))).thenReturn(response("""
                {"goal":"Laptop","maxAmount":1000,"currency":"USD"}
                """));

        ResponseStatusException exception = org.junit.jupiter.api.Assertions.assertThrows(
                ResponseStatusException.class, () -> model.extract("Buy a laptop under $1,000"));

        assertEquals(502, exception.getStatusCode().value());
        assertTrue(exception.getReason().contains("invalid intent draft"));
    }

    private static ConverseResponse response(String text) {
        return ConverseResponse.builder()
                .output(ConverseOutput.builder()
                        .message(Message.builder()
                                .role(ConversationRole.ASSISTANT)
                                .content(ContentBlock.fromText(text))
                                .build())
                        .build())
                .build();
    }
}
