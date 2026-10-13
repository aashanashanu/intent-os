package com.intentos.paypal;

import com.intentos.intent.TransactionProposal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PayPalOrdersServiceTest {
    @Test
    void createsCaptureOrderWithServerCalculatedAmountAndBuyerApprovalLink() {
        RestClient.Builder clientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
        server.expect(requestTo("https://api-m.sandbox.paypal.com/v1/oauth2/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", org.hamcrest.Matchers.startsWith("Basic ")))
                .andRespond(withSuccess("""
                        {"access_token":"sandbox-token","expires_in":3600}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api-m.sandbox.paypal.com/v2/checkout/orders"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sandbox-token"))
                .andExpect(header("PayPal-Request-Id", org.hamcrest.Matchers.notNullValue()))
                .andExpect(content().json("""
                        {
                          "intent":"CAPTURE",
                          "purchase_units":[{
                            "reference_id":"PROP-1",
                            "custom_id":"INT-1",
                                                                                                                "amount":{
                                                                                                                        "currency_code":"USD","value":"48.00",
                                                                                                                        "breakdown":{
                                                                                                                                "item_total":{"currency_code":"USD","value":"48.00"},
                                                                                                                                "shipping":{"currency_code":"USD","value":"0.00"},
                                                                                                                                "tax_total":{"currency_code":"USD","value":"0.00"}
                                                                                                                        }
                                                                                                                },
                                                                                                                "items":[{"name":"Laptop","quantity":"1","unit_amount":{"currency_code":"USD","value":"48.00"}}]
                          }],
                          "application_context":{
                            "return_url":"http://localhost:5173/?paypal=return",
                            "cancel_url":"http://localhost:5173/?paypal=cancel"
                          }
                        }
                        """))
                .andRespond(withSuccess("""
                        {"id":"ORDER-1","status":"CREATED","links":[
                          {"rel":"payer-action","href":"https://www.sandbox.paypal.com/checkoutnow?token=ORDER-1"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        PayPalOrdersService service = new PayPalOrdersService(
                "client-id", "client-secret", "sandbox", "http://localhost:5173/", "", clientBuilder);
        var proposal = new TransactionProposal("PROP-1", "INT-1", "USD",
                List.of(new TransactionProposal.Item("Laptop", "laptop", 1, new BigDecimal("48.00"),
                        false, false, false, null, Map.of())),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("999.00"));

        PayPalOrderResponse response = service.createOrder(
                "INT-1", proposal, new BigDecimal("48.00"), "USD");

        assertEquals("ORDER-1", response.orderId());
        assertEquals("CREATED", response.status());
        assertEquals("https://www.sandbox.paypal.com/checkoutnow?token=ORDER-1", response.approvalUrl());
        server.verify();
    }

                @Test
                void doesNotCaptureUntilPayPalReportsBuyerApproval() {
                                RestClient.Builder clientBuilder = RestClient.builder();
                                MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
                                expectToken(server);
                                server.expect(requestTo("https://api-m.sandbox.paypal.com/v2/checkout/orders/ORDER-1"))
                                                                .andExpect(method(HttpMethod.GET))
                                                                .andRespond(withSuccess("""
                                                                                                {"id":"ORDER-1","status":"CREATED","purchase_units":[{
                                                                                                        "custom_id":"INT-1","reference_id":"PROP-1",
                                                                                                        "amount":{"currency_code":"USD","value":"48.00"}
                                                                                                }]}
                                                                                                """, MediaType.APPLICATION_JSON));

                                ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                                                                () -> service(clientBuilder).captureOrder(checkout()));

                                assertEquals(409, exception.getStatusCode().value());
                                server.verify();
                }

                @Test
                void capturesOnlyTheMatchingApprovedOrder() {
                                RestClient.Builder clientBuilder = RestClient.builder();
                                MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
                                expectToken(server);
                                server.expect(requestTo("https://api-m.sandbox.paypal.com/v2/checkout/orders/ORDER-1"))
                                                                .andExpect(method(HttpMethod.GET))
                                                                .andRespond(withSuccess("""
                                                                                                {"id":"ORDER-1","status":"APPROVED","purchase_units":[{
                                                                                                        "custom_id":"INT-1","reference_id":"PROP-1",
                                                                                                        "amount":{"currency_code":"USD","value":"48.00"}
                                                                                                }]}
                                                                                                """, MediaType.APPLICATION_JSON));
                                server.expect(requestTo("https://api-m.sandbox.paypal.com/v2/checkout/orders/ORDER-1/capture"))
                                                                .andExpect(method(HttpMethod.POST))
                                                                .andExpect(header("Authorization", "Bearer sandbox-token"))
                                                                .andExpect(header("PayPal-Request-Id", org.hamcrest.Matchers.notNullValue()))
                                                                .andRespond(withSuccess("""
                                                                                                {"id":"ORDER-1","status":"COMPLETED","purchase_units":[{
                                                                                                        "payments":{"captures":[{
                                                                                                                "id":"CAPTURE-1","status":"COMPLETED",
                                                                                                                "amount":{"currency_code":"USD","value":"48.00"}
                                                                                                        }]}
                                                                                                }]}
                                                                                                """, MediaType.APPLICATION_JSON));

                                PayPalCaptureResponse result = service(clientBuilder).captureOrder(checkout());

                                assertEquals("ORDER-1", result.orderId());
                                assertEquals("CAPTURE-1", result.captureId());
                                assertEquals("COMPLETED", result.status());
                                assertEquals(0, result.amount().compareTo(new BigDecimal("48.00")));
                                assertEquals("USD", result.currency());
                                server.verify();
                }

                @Test
                void recoversCaptureEvidenceWhenPayPalAlreadyCompletedTheOrder() {
                                RestClient.Builder clientBuilder = RestClient.builder();
                                MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
                                expectToken(server);
                                server.expect(requestTo("https://api-m.sandbox.paypal.com/v2/checkout/orders/ORDER-1"))
                                                                .andExpect(method(HttpMethod.GET))
                                                                .andRespond(withSuccess("""
                                                                                                {"id":"ORDER-1","status":"COMPLETED","purchase_units":[{
                                                                                                        "custom_id":"INT-1","reference_id":"PROP-1",
                                                                                                        "amount":{"currency_code":"USD","value":"48.00"},
                                                                                                        "payments":{"captures":[{
                                                                                                                "id":"CAPTURE-EXISTING","status":"COMPLETED",
                                                                                                                "amount":{"currency_code":"USD","value":"48.00"}
                                                                                                        }]}
                                                                                                }]}
                                                                                                """, MediaType.APPLICATION_JSON));

                                PayPalCaptureResponse result = service(clientBuilder).captureOrder(checkout());

                                assertEquals("CAPTURE-EXISTING", result.captureId());
                                assertEquals("COMPLETED", result.status());
                                server.verify();
                }

                            @Test
                            void asksPayPalToVerifyWebhookTransmissionHeadersAndEvent() throws Exception {
                                RestClient.Builder clientBuilder = RestClient.builder();
                                MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
                                expectToken(server);
                                server.expect(requestTo("https://api-m.sandbox.paypal.com/v1/notifications/verify-webhook-signature"))
                                        .andExpect(method(HttpMethod.POST))
                                        .andExpect(header("Authorization", "Bearer sandbox-token"))
                                        .andExpect(content().json("""
                                                {
                                                  "auth_algo":"SHA256withRSA",
                                                  "cert_url":"https://api-m.sandbox.paypal.com/cert",
                                                  "transmission_id":"TRANSMISSION-1",
                                                  "transmission_sig":"signature-value",
                                                  "transmission_time":"2026-10-12T12:00:00Z",
                                                  "webhook_id":"WEBHOOK-1",
                                                  "webhook_event":{"id":"EVENT-1","event_type":"PAYMENT.CAPTURE.COMPLETED"}
                                                }
                                                """))
                                        .andRespond(withSuccess("""
                                                {"verification_status":"SUCCESS"}
                                                """, MediaType.APPLICATION_JSON));
                                PayPalOrdersService service = new PayPalOrdersService(
                                        "client-id", "client-secret", "sandbox", "http://localhost:5173", "WEBHOOK-1", clientBuilder);

                                assertTrue(service.verifyWebhook(
                                        new ObjectMapper().readTree("{\"id\":\"EVENT-1\",\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\"}"),
                                        "TRANSMISSION-1", "2026-10-12T12:00:00Z", "signature-value",
                                        "https://api-m.sandbox.paypal.com/cert", "SHA256withRSA"));
                                server.verify();
                            }

                private static void expectToken(MockRestServiceServer server) {
                                server.expect(requestTo("https://api-m.sandbox.paypal.com/v1/oauth2/token"))
                                                                .andExpect(method(HttpMethod.POST))
                                                                .andRespond(withSuccess("""
                                                                                                {"access_token":"sandbox-token","expires_in":3600}
                                                                                                """, MediaType.APPLICATION_JSON));
                }

                private static PayPalOrdersService service(RestClient.Builder clientBuilder) {
                                return new PayPalOrdersService("client-id", "client-secret", "sandbox", "http://localhost:5173", "", clientBuilder);
                }

                private static PayPalCheckoutSession checkout() {
                        return new PayPalCheckoutSession("ORDER-1", "INT-1", "PROP-1",
                                new TransactionProposal("PROP-1", "INT-1", "USD", List.of(), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("48.00")),
                                new BigDecimal("48.00"),
                                                                "USD", PayPalCheckoutSession.State.CREATED, null);
                }
}