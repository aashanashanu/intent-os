package com.intentos.intent;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "intentos.auth.required=false")
@AutoConfigureMockMvc
class IntentExtractionApiTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IntentLanguageModel languageModel;

    @Test
    void returnsStructuredDraftFromNaturalLanguage() throws Exception {
        when(languageModel.extract("Find me a laptop with 16GB RAM under $1,000. No subscriptions."))
                .thenReturn(new IntentExtractionResult(
                        "Find me a laptop with 16GB RAM under $1,000. No subscriptions.", "Laptop",
                        new BigDecimal("1000"), "USD", Set.of("laptop"), Map.of("ram", "16GB"),
                        false, false, false, new BigDecimal("0.94"), false, List.of()));

        mockMvc.perform(post("/api/intents/extract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"naturalLanguageRequest":"Find me a laptop with 16GB RAM under $1,000. No subscriptions."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goal").value("Laptop"))
                .andExpect(jsonPath("$.maxAmount").value(1000))
                .andExpect(jsonPath("$.allowedCategories[0]").value("laptop"))
                .andExpect(jsonPath("$.requiredAttributes.ram").value("16GB"))
                .andExpect(jsonPath("$.allowSubscriptions").value(false))
                .andExpect(jsonPath("$.needsClarification").value(false));
    }

    @Test
    void returnsClarificationQuestionsWhenRequestIsAmbiguous() throws Exception {
        when(languageModel.extract("Please help me shop."))
                .thenReturn(new IntentExtractionResult(
                        "Please help me shop.", null, null, "USD", Set.of(), Map.of(), false, false, false,
                        new BigDecimal("0.4"), true,
                        List.of("What type of purchase are you looking for?", "What is your maximum budget in USD?")));

        mockMvc.perform(post("/api/intents/extract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"naturalLanguageRequest\":\"Please help me shop.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.needsClarification").value(true))
                .andExpect(jsonPath("$.clarificationQuestions.length()").value(2))
                .andExpect(jsonPath("$.maxAmount").doesNotExist());
    }

    @Test
    void rejectsBlankRequest() throws Exception {
        mockMvc.perform(post("/api/intents/extract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"naturalLanguageRequest\":\" \"}"))
                .andExpect(status().isBadRequest());
    }
}
