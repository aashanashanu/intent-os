package com.intentos.intent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "intentos.auth.required=false")
@AutoConfigureMockMvc
class IntentDecisionEventApiTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void validationDecisionIsRecordedAndReturnedInIntentActivity() throws Exception {
        var created = mockMvc.perform(post("/api/intents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"activity-user","naturalLanguageRequest":"Buy a laptop under $500","goal":"Laptop","maxAmount":500,"currency":"USD","allowedCategories":["laptop"]}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode contract = objectMapper.readTree(created.getResponse().getContentAsString());
        String intentId = contract.path("intentId").asText();

        mockMvc.perform(post("/api/intents/{intentId}/validation", intentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"proposalId":"PROP-ACTIVITY","intentId":"%s","currency":"USD","items":[{"name":"Laptop","category":"laptop","quantity":1,"unitPrice":400}],"shipping":0,"tax":0,"total":400}
                                """.formatted(intentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("APPROVE"));

        mockMvc.perform(get("/api/intents/{intentId}/events", intentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").value("VALIDATION"))
                .andExpect(jsonPath("$[0].proposalId").value("PROP-ACTIVITY"))
                .andExpect(jsonPath("$[0].decision").value("APPROVE"))
                .andExpect(jsonPath("$[0].violations.length()").value(0));
    }
}
