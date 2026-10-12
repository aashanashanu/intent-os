package com.intentos.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PayPalAuthControllerTest {
    @Test
    void redirectsToUiWhenPayPalRejectsAuthorizationCodeExchange() {
        RestClient.Builder clientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
        server.expect(requestTo("https://api-m.sandbox.paypal.com/v1/oauth2/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withUnauthorizedRequest());
        PayPalAuthController controller = new PayPalAuthController(
                "client-id", "client-secret", "sandbox", "https://app.example/api/auth/paypal/callback",
                "https://app.example", clientBuilder);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("paypalLoginState", "valid-state");
        request.setSession(session);

        var response = controller.completeLogin("authorization-code", "valid-state", null, request);

        assertEquals(302, response.getStatusCode().value());
        assertEquals("https://app.example?paypalLoginError=login_failed",
                response.getHeaders().getLocation().toString());
        server.verify();
    }
}