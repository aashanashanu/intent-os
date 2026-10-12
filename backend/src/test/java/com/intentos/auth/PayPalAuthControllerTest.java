package com.intentos.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PayPalAuthControllerTest {
    @Test
    void exchangesCodeLoadsPayPalProfileAndAuthenticatesSession() {
        RestClient.Builder clientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
        server.expect(requestTo("https://api-m.sandbox.paypal.com/v1/oauth2/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(
                        "grant_type=authorization_code&code=authorization-code&redirect_uri=https%3A%2F%2Fapp.example%2Fapi%2Fauth%2Fpaypal%2Fcallback"))
                .andRespond(withSuccess("{\"access_token\":\"profile-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(
                        "https://api-m.sandbox.paypal.com/v1/identity/openidconnect/userinfo/?schema=paypalv1.1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer profile-token"))
                .andRespond(withSuccess(
                        "{\"user_id\":\"sandbox-buyer\",\"name\":\"Sandbox Buyer\",\"email\":\"buyer@example.com\"}",
                        MediaType.APPLICATION_JSON));
        PayPalAuthController controller = new PayPalAuthController(
                "client-id", "client-secret", "sandbox", "https://app.example/api/auth/paypal/callback",
                "https://app.example", clientBuilder);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("paypalLoginState", "valid-state");
        request.setSession(session);

        var response = controller.completeLogin("authorization-code", "valid-state", null, request);

        assertEquals(302, response.getStatusCode().value());
        assertEquals("https://app.example", response.getHeaders().getLocation().toString());
        assertEquals("sandbox-buyer", request.getSession(false).getAttribute(PayPalAuthController.USER_ID_SESSION_ATTRIBUTE));
        assertEquals("Sandbox Buyer", request.getSession(false).getAttribute("paypalUserName"));
        server.verify();
    }

    @Test
    void redirectsToUiWhenPayPalRejectsAuthorizationCodeExchange() {
        RestClient.Builder clientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
        server.expect(requestTo("https://api-m.sandbox.paypal.com/v1/oauth2/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(
                        "grant_type=authorization_code&code=authorization-code&redirect_uri=https%3A%2F%2Fapp.example%2Fapi%2Fauth%2Fpaypal%2Fcallback"))
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
        assertEquals("https://app.example?paypalLoginError=login_failed&paypalLoginStage=token&paypalLoginStatus=401&paypalLoginDetail=unknown",
                response.getHeaders().getLocation().toString());
        server.verify();
    }
}