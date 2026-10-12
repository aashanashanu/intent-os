package com.intentos.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class IntentApiTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createsContractAndValidatesProposalAgainstStoredPolicy() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/intents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "USER-1",
                                  "naturalLanguageRequest": "Buy a development laptop under $1000",
                                  "goal": "Development laptop",
                                  "maxAmount": 1000,
                                  "currency": "USD",
                                  "allowedCategories": ["laptop"],
                                  "requiredAttributes": {"ram": "16GB"},
                                  "maximumQuantities": {"Laptop": 1},
                                  "allowRecurringPayments": false,
                                  "allowSubscriptions": false,
                                  "allowUnrequestedAddons": false
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.approvalPolicy").value("BUYER_APPROVAL"))
                .andReturn();
        JsonNode contract = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String intentId = contract.get("intentId").asText();

        mockMvc.perform(post("/api/intents/{intentId}/validation", intentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "proposalId": "PROP-1",
                                  "intentId": "%s",
                                  "currency": "USD",
                                  "items": [
                                    {"name":"Laptop","category":"laptop","quantity":1,"unitPrice":899,"attributes":{"ram":"32GB"}},
                                    {"name":"Warranty","category":"laptop","quantity":1,"unitPrice":199,"addOn":true},
                                    {"name":"Antivirus","category":"laptop","quantity":1,"unitPrice":79,"recurring":true,"subscription":true,"addOn":true}
                                  ],
                                  "shipping": 0,
                                  "tax": 72,
                                  "total": 1249
                                }
                                """.formatted(intentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("BLOCK"))
                .andExpect(jsonPath("$.calculatedTotal").value(1249))
                .andExpect(jsonPath("$.violations[?(@.type == 'BUDGET_EXCEEDED')]").exists());

        mockMvc.perform(post("/api/intents/{intentId}/repair", intentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "proposalId":"PROP-1",
                                  "intentId":"%s",
                                  "currency":"USD",
                                  "items":[
                                    {"name":"Laptop","category":"laptop","quantity":1,"unitPrice":899,"attributes":{"ram":"32GB"}},
                                    {"name":"Warranty","category":"laptop","quantity":1,"unitPrice":199,"addOn":true},
                                    {"name":"Antivirus","category":"laptop","quantity":1,"unitPrice":79,"recurring":true,"subscription":true,"addOn":true}
                                  ],
                                  "shipping":0,
                                  "tax":72,
                                  "total":1249
                                }
                                """.formatted(intentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.attempts").value(1))
                .andExpect(jsonPath("$.proposal.total").value(971))
                .andExpect(jsonPath("$.proposal.items.length()").value(1));

        assertEquals(intentId, contract.get("intentId").asText());
    }

    @Test
    void rejectsNonPositiveBudget() throws Exception {
        mockMvc.perform(post("/api/intents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"USER-1","naturalLanguageRequest":"Buy a laptop","goal":"Laptop","maxAmount":0,"currency":"USD"}
                                """))
                .andExpect(status().isBadRequest());
    }
}