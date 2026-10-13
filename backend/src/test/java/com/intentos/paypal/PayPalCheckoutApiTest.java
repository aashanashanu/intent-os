package com.intentos.paypal;

import com.intentos.intent.TransactionProposal;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PayPalCheckoutApiTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PayPalOrdersService ordersService;

    @Test
    void blockedProposalCannotCreatePayPalOrder() throws Exception {
        String intentId = createIntent("10");

        mockMvc.perform(post("/api/intents/{intentId}/orders", intentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(proposal(intentId, "48.00")))
                .andExpect(status().isConflict());

        verifyNoInteractions(ordersService);
    }

    @Test
    void approvedProposalCreatesOrderUsingValidatedTotalAndContractCurrency() throws Exception {
        String intentId = createIntent("100");
        when(ordersService.createOrder(anyString(), any(TransactionProposal.class), any(BigDecimal.class), anyString()))
                .thenReturn(new PayPalOrderResponse("ORDER-1", "CREATED", "https://paypal.test/approve"));

        mockMvc.perform(post("/api/intents/{intentId}/orders", intentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(proposal(intentId, "48.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("ORDER-1"))
                .andExpect(jsonPath("$.approvalUrl").value("https://paypal.test/approve"));

        verify(ordersService).createOrder(
                org.mockito.ArgumentMatchers.eq(intentId),
                any(TransactionProposal.class),
                argThat(amount -> amount.compareTo(new BigDecimal("48.00")) == 0),
                org.mockito.ArgumentMatchers.eq("USD"));
    }

    @Test
    void duplicateCaptureReturnsStoredResultWithoutCallingPayPalAgain() throws Exception {
        String intentId = createIntent("100");
        when(ordersService.createOrder(anyString(), any(TransactionProposal.class), any(BigDecimal.class), anyString()))
                .thenReturn(new PayPalOrderResponse("ORDER-REPLAY", "CREATED", "https://paypal.test/approve"));
        mockMvc.perform(post("/api/intents/{intentId}/orders", intentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(proposal(intentId, "48.00")))
                .andExpect(status().isOk());

        PayPalCaptureResponse capture = new PayPalCaptureResponse(
                "ORDER-REPLAY", "COMPLETED", "CAPTURE-REPLAY", new BigDecimal("48.00"), "USD");
        when(ordersService.captureOrder(any(PayPalCheckoutSession.class))).thenReturn(capture);

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/api/intents/{intentId}/orders/{orderId}/capture", intentId, "ORDER-REPLAY"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.captureId").value("CAPTURE-REPLAY"));
        }

        verify(ordersService, times(1)).captureOrder(any(PayPalCheckoutSession.class));
    }

    @Test
    void cannotCaptureAnOrderThroughAnotherIntent() throws Exception {
        String orderIntentId = createIntent("100");
        String otherIntentId = createIntent("100");
        when(ordersService.createOrder(anyString(), any(TransactionProposal.class), any(BigDecimal.class), anyString()))
                .thenReturn(new PayPalOrderResponse("ORDER-BOUND", "CREATED", "https://paypal.test/approve"));
        mockMvc.perform(post("/api/intents/{intentId}/orders", orderIntentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(proposal(orderIntentId, "48.00")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/intents/{intentId}/orders/{orderId}/capture", otherIntentId, "ORDER-BOUND"))
                .andExpect(status().isNotFound());

        verify(ordersService, org.mockito.Mockito.never()).captureOrder(any(PayPalCheckoutSession.class));
    }

    private String createIntent(String maximumAmount) throws Exception {
        var result = mockMvc.perform(post("/api/intents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId":"USER-PAYMENT",
                                  "naturalLanguageRequest":"Buy one laptop under $%s",
                                  "goal":"Laptop",
                                  "maxAmount":%s,
                                  "currency":"USD",
                                  "allowedCategories":["laptop"],
                                  "maximumQuantities":{"Laptop":1}
                                }
                                """.formatted(maximumAmount, maximumAmount)))
                .andExpect(status().isCreated())
                .andReturn();
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(result.getResponse().getContentAsString()).path("intentId").asText();
    }

    private static String proposal(String intentId, String submittedTotal) {
        return """
                {
                  "proposalId":"PROP-PAYMENT",
                  "intentId":"%s",
                  "currency":"USD",
                  "items":[{"name":"Laptop","category":"laptop","quantity":1,"unitPrice":48}],
                  "shipping":0,
                  "tax":0,
                  "total":%s
                }
                """.formatted(intentId, submittedTotal);
    }
}