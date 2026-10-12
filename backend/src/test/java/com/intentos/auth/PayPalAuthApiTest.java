package com.intentos.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PayPalAuthApiTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void reportsLoginUnconfiguredWithoutCredentials() throws Exception {
        mockMvc.perform(get("/api/auth/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.loginConfigured").value(false));
    }

    @Test
    void refusesToStartLoginWithoutCredentials() throws Exception {
        mockMvc.perform(get("/api/auth/paypal"))
                .andExpect(status().isServiceUnavailable());
    }

        @Test
        void redirectsPayPalConsentDenialBackToUiAfterValidatingState() throws Exception {
                MockHttpSession session = new MockHttpSession();
                session.setAttribute("paypalLoginState", "expected-state");

                mockMvc.perform(get("/api/auth/paypal/callback")
                                                .session(session)
                                                .param("error", "access_denied")
                                                .param("state", "expected-state"))
                                .andExpect(status().isFound())
                                .andExpect(header().string("Location", "http://localhost:5173?paypalLoginError=access_denied"));
        }

        @Test
        void rejectsPayPalConsentDenialWithInvalidState() throws Exception {
                MockHttpSession session = new MockHttpSession();
                session.setAttribute("paypalLoginState", "expected-state");

                mockMvc.perform(get("/api/auth/paypal/callback")
                                                .session(session)
                                                .param("error", "access_denied")
                                                .param("state", "wrong-state"))
                                .andExpect(status().isBadRequest());
        }

    @Test
    void contractIsOwnedByAuthenticatedPayPalUser() throws Exception {
        MockHttpSession ownerSession = new MockHttpSession();
        ownerSession.setAttribute(PayPalAuthController.USER_ID_SESSION_ATTRIBUTE, "paypal-user-a");
        ownerSession.setAttribute(PayPalAuthController.CSRF_SESSION_ATTRIBUTE, "csrf-a");
        var created = mockMvc.perform(post("/api/intents")
                        .session(ownerSession)
                .header("X-CSRF-Token", "csrf-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"spoofed-user","naturalLanguageRequest":"Buy a laptop","goal":"Laptop","maxAmount":1000,"currency":"USD"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value("paypal-user-a"))
                .andReturn();
        String intentId = objectMapper.readTree(created.getResponse().getContentAsString()).path("intentId").asText();

        MockHttpSession otherSession = new MockHttpSession();
        otherSession.setAttribute(PayPalAuthController.USER_ID_SESSION_ATTRIBUTE, "paypal-user-b");
        otherSession.setAttribute(PayPalAuthController.CSRF_SESSION_ATTRIBUTE, "csrf-b");
        mockMvc.perform(get("/api/intents/{intentId}", intentId).session(otherSession))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsUnsafeAuthenticatedRequestWithoutCsrfToken() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(PayPalAuthController.USER_ID_SESSION_ATTRIBUTE, "paypal-user-a");
        session.setAttribute(PayPalAuthController.CSRF_SESSION_ATTRIBUTE, "csrf-a");

        mockMvc.perform(post("/api/intents")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"naturalLanguageRequest":"Buy a laptop","goal":"Laptop","maxAmount":1000,"currency":"USD"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("CSRF_TOKEN_INVALID"));
    }
}
