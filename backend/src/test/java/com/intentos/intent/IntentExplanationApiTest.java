package com.intentos.intent;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.intentos.validation.ValidationDecision;

@SpringBootTest(properties = "intentos.auth.required=false")
@AutoConfigureMockMvc
class IntentExplanationApiTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IntentDecisionEventStore eventStore;

    @MockitoBean
    private IntentLanguageModel languageModel;

    @Test
    void explanationUsesPersistedIntentEvidenceAndCallsExplanationRole() throws Exception {
        var created = mockMvc.perform(post("/api/intents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"explanation-user","naturalLanguageRequest":"Buy a laptop under $500","goal":"Laptop","maxAmount":500,"currency":"USD"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode contract = objectMapper.readTree(created.getResponse().getContentAsString());
        String intentId = contract.path("intentId").asText();
        eventStore.append(new IntentDecisionEvent("EVT-EXPLAIN-1", intentId, "PROP-1", Instant.now(),
                "VALIDATION", ValidationDecision.BLOCK, new BigDecimal("700"), List.of(), "Budget exceeded."));
        when(languageModel.generateAdvisory(eq(IntentModelTask.EXPLANATION), eq(
                "Explain the recorded IntentOS decision to the user in concise plain language.\n"
                        + "Use only the supplied intent and event evidence. Distinguish attempted, repaired, approved, and paid states.\n"
                        + "Never claim payment occurred unless an authoritative payment event explicitly records it.\n"
                        + "Do not make or revise policy decisions. If evidence is incomplete, say what is unknown.\n"), contains("Budget exceeded.")))
                .thenReturn(Optional.of("The proposal exceeded your $500 limit and was blocked. No payment is recorded."));

        mockMvc.perform(get("/api/intents/{intentId}/explanation", intentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.explanation").value("The proposal exceeded your $500 limit and was blocked. No payment is recorded."))
                .andExpect(jsonPath("$.eventCount").value(1));

        verify(languageModel).generateAdvisory(eq(IntentModelTask.EXPLANATION), eq(
                "Explain the recorded IntentOS decision to the user in concise plain language.\n"
                        + "Use only the supplied intent and event evidence. Distinguish attempted, repaired, approved, and paid states.\n"
                        + "Never claim payment occurred unless an authoritative payment event explicitly records it.\n"
                        + "Do not make or revise policy decisions. If evidence is incomplete, say what is unknown.\n"), contains("Budget exceeded."));
    }
}
