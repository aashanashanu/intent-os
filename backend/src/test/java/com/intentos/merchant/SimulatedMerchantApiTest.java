package com.intentos.merchant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intentos.auth.PayPalAuthController;
import com.intentos.intent.IntentLanguageModel;
import com.intentos.intent.ShoppingSelection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "intentos.auth.required=true",
        "intentos.elastic.enabled=false",
        "intentos.llm.enabled=false"
})
@AutoConfigureMockMvc
class SimulatedMerchantApiTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
        @MockBean private IntentLanguageModel languageModel;

    @Test
    void merchantEndpointsRequireSessionAndIntentOwnership() throws Exception {
        mvc.perform(get("/api/intents/unknown/merchant/products")).andExpect(status().isUnauthorized());
        MockHttpSession owner = session("merchant-owner");
        String intentId = createIntent(owner);
        mvc.perform(get("/api/intents/{id}/merchant/products", intentId).session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulation").value(true))
                .andExpect(jsonPath("$.source").value("simulated-merchant"));
        mvc.perform(get("/api/intents/{id}/merchant/products", intentId).session(session("other-owner")))
                .andExpect(status().isNotFound());
    }

    @Test
    void quoteRequiresCsrfAndRejectsLookupThroughDifferentIntent() throws Exception {
        MockHttpSession owner = session("quote-owner");
        String intentId = createIntent(owner);
        String otherIntentId = createIntent(owner);
        String body = """
                {"lines":[{"productId":"portable-computer","variantId":"16gb-512gb","quantity":1}],
                 "country":"US","region":"CA","shippingMethod":"standard"}
                """;
        mvc.perform(post("/api/intents/{id}/merchant/quotes", intentId).session(owner)
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        var result = mvc.perform(post("/api/intents/{id}/merchant/quotes", intentId).session(owner)
                        .header("X-CSRF-Token", "merchant-csrf").contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.total").value(854.4))
                .andReturn();
        String quoteId = mapper.readTree(result.getResponse().getContentAsString()).path("quoteId").asText();
        mvc.perform(get("/api/intents/{id}/merchant/quotes/{quoteId}", otherIntentId, quoteId).session(owner))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/intents/{id}/merchant/quotes/{quoteId}/verify", intentId, quoteId).session(owner)
                        .header("X-CSRF-Token", "merchant-csrf"))
                .andExpect(status().isOk());
    }

    @Test
    void shoppingRunRevisionsAreAppendOnlyAndBoundToQuoteItems() throws Exception {
        MockHttpSession owner = session("lineage-owner");
        String intentId = createIntent(owner);
        String quoteBody = """
                {"lines":[{"productId":"portable-computer","variantId":"16gb-512gb","quantity":1}],
                 "country":"US","region":"CA","shippingMethod":"standard"}
                """;
        var quoteResponse = mvc.perform(post("/api/intents/{id}/merchant/quotes", intentId).session(owner)
                        .header("X-CSRF-Token", "merchant-csrf").contentType("application/json").content(quoteBody))
                .andExpect(status().isOk()).andReturn();
        String quoteId = mapper.readTree(quoteResponse.getResponse().getContentAsString()).path("quoteId").asText();
        var runResponse = mvc.perform(post("/api/intents/{id}/shopping-runs", intentId).session(owner)
                        .header("X-CSRF-Token", "merchant-csrf"))
                .andExpect(status().isCreated()).andReturn();
        String runId = mapper.readTree(runResponse.getResponse().getContentAsString()).path("runId").asText();

        String firstRevision = revisionBody(quoteId, 1);
        var firstResponse = mvc.perform(post("/api/intents/{id}/shopping-runs/{runId}/revisions", intentId, runId)
                        .session(owner).header("X-CSRF-Token", "merchant-csrf")
                        .contentType("application/json").content(firstRevision))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.revisionNumber").value(1))
                .andExpect(jsonPath("$.parentRevisionId").doesNotExist()).andReturn();
        String firstRevisionId = mapper.readTree(firstResponse.getResponse().getContentAsString())
                .path("revisionId").asText();

        mvc.perform(post("/api/intents/{id}/shopping-runs/{runId}/revisions", intentId, runId)
                        .session(owner).header("X-CSRF-Token", "merchant-csrf")
                        .contentType("application/json").content(revisionBody(quoteId, 1)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.revisionNumber").value(2))
                .andExpect(jsonPath("$.parentRevisionId").value(firstRevisionId));
        mvc.perform(post("/api/intents/{id}/shopping-runs/{runId}/revisions", intentId, runId)
                        .session(owner).header("X-CSRF-Token", "merchant-csrf")
                        .contentType("application/json").content(revisionBody(quoteId, 2)))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/intents/{id}/shopping-runs/{runId}/revisions", intentId, runId).session(owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].revisionId").value(firstRevisionId))
                .andExpect(jsonPath("$[0].items[0].itemId").isNotEmpty())
                .andExpect(jsonPath("$[1].parentRevisionId").value(firstRevisionId));
    }

    @Test
    void shoppingRunUsesModelCandidateIdsAndRequiresExplicitRevisionReview() throws Exception {
        MockHttpSession owner = session("autonomous-owner");
        String intentId = createIntent(owner);
        var runResponse = mvc.perform(post("/api/intents/{id}/shopping-runs", intentId).session(owner)
                        .header("X-CSRF-Token", "merchant-csrf"))
                .andExpect(status().isCreated()).andReturn();
        String runId = mapper.readTree(runResponse.getResponse().getContentAsString()).path("runId").asText();
        when(languageModel.selectShopping(any(), any())).thenReturn(Optional.of(new ShoppingSelection(
                List.of(new ShoppingSelection.SelectedCandidate("portable-computer:16gb-512gb", 1)),
                "Matches the requested laptop within the budget", "test-nova-lite")));

        var proposalResponse = mvc.perform(post("/api/intents/{id}/shopping-runs/{runId}/propose", intentId, runId)
                        .session(owner).header("X-CSRF-Token", "merchant-csrf")
                        .contentType("application/json")
                        .content("{\"country\":\"US\",\"region\":\"CA\",\"shippingMethod\":\"standard\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quote.status").value("READY"))
                .andExpect(jsonPath("$.validation.decision").value("APPROVE"))
                .andExpect(jsonPath("$.revision.modelId").value("test-nova-lite"))
                .andReturn();
        String revisionId = mapper.readTree(proposalResponse.getResponse().getContentAsString())
                .path("revision").path("revisionId").asText();
        mvc.perform(post("/api/intents/{id}/shopping-runs/{runId}/revisions/{revisionId}/review",
                        intentId, runId, revisionId).session(owner).header("X-CSRF-Token", "merchant-csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revisionId").value(revisionId))
                .andExpect(jsonPath("$.userId").value("autonomous-owner"));
    }

    @Test
    void repairCreatesFreshQuoteBackedChildRevisionInSameRun() throws Exception {
        MockHttpSession owner = session("repair-owner");
        String intentId = createIntent(owner);
        var runResponse = mvc.perform(post("/api/intents/{id}/shopping-runs", intentId).session(owner)
                        .header("X-CSRF-Token", "merchant-csrf"))
                .andExpect(status().isCreated()).andReturn();
        String runId = mapper.readTree(runResponse.getResponse().getContentAsString()).path("runId").asText();
        when(languageModel.selectShopping(any(), any())).thenReturn(Optional.of(new ShoppingSelection(List.of(
                new ShoppingSelection.SelectedCandidate("portable-computer:16gb-512gb", 1),
                new ShoppingSelection.SelectedCandidate("support-plan:monthly", 1)),
                "Selected laptop and optional support", "test-nova-lite")));
        var initial = mvc.perform(post("/api/intents/{id}/shopping-runs/{runId}/propose", intentId, runId)
                        .session(owner).header("X-CSRF-Token", "merchant-csrf")
                        .contentType("application/json")
                        .content("{\"country\":\"US\",\"region\":\"CA\",\"shippingMethod\":\"standard\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validation.decision").value("BLOCK"))
                .andReturn();
        String firstRevisionId = mapper.readTree(initial.getResponse().getContentAsString())
                .path("revision").path("revisionId").asText();

        mvc.perform(post("/api/intents/{id}/shopping-runs/{runId}/revisions/{revisionId}/repair",
                        intentId, runId, firstRevisionId).session(owner).header("X-CSRF-Token", "merchant-csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validation.decision").value("APPROVE"))
                .andExpect(jsonPath("$.revision.revisionNumber").value(2))
                .andExpect(jsonPath("$.revision.parentRevisionId").value(firstRevisionId))
                .andExpect(jsonPath("$.revision.items.length()").value(1))
                .andExpect(jsonPath("$.quote.quoteId").isNotEmpty());
        mvc.perform(get("/api/intents/{id}/shopping-runs/{runId}/revisions", intentId, runId).session(owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].revisionId").value(firstRevisionId));
    }

    @Test
    void shoppingHistoryRequiresPrincipalAndOnlyReturnsOwnedRuns() throws Exception {
        mvc.perform(get("/api/shopping-history")).andExpect(status().isUnauthorized());
        MockHttpSession owner = session("history-owner");
        String ownedIntent = createIntent(owner);
        String foreignIntent = createIntent(session("foreign-owner"));
        mvc.perform(post("/api/intents/{id}/shopping-runs", ownedIntent).session(owner)
                        .header("X-CSRF-Token", "merchant-csrf"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/intents/{id}/shopping-runs", foreignIntent).session(session("foreign-owner"))
                        .header("X-CSRF-Token", "merchant-csrf"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/shopping-history?page=0&pageSize=1").session(owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].intentId").value(ownedIntent));
    }

    private static String revisionBody(String quoteId, int quantity) {
        return """
                {"quoteId":"%s","items":[{"productId":"portable-computer",
                 "variantId":"16gb-512gb","quantity":%d}],"candidateIds":["candidate-1"],
                 "selectionRationale":"Selected from returned candidates","modelId":"test-model",
                 "modelTask":"SHOPPING","validationDecision":"APPROVE"}
                """.formatted(quoteId, quantity);
    }

    private MockHttpSession session(String userId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(PayPalAuthController.USER_ID_SESSION_ATTRIBUTE, userId);
        session.setAttribute(PayPalAuthController.CSRF_SESSION_ATTRIBUTE, "merchant-csrf");
        return session;
    }

    private String createIntent(MockHttpSession session) throws Exception {
        var response = mvc.perform(post("/api/intents").session(session)
                        .header("X-CSRF-Token", "merchant-csrf").contentType("application/json")
                        .content("""
                                {"naturalLanguageRequest":"Buy a laptop under $1000","goal":"Laptop",
                                 "maxAmount":1000,"currency":"USD"}
                                """))
                .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(response.getResponse().getContentAsString()).path("intentId").asText();
    }
}