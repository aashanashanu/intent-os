package com.intentos.paypal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PayPalCatalogServiceTest {
    @Test
    void exchangesSandboxCredentialsThenListsProducts() {
        RestClient.Builder clientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
        String basicCredentials = Base64.getEncoder()
                .encodeToString("client-id:client-secret".getBytes(StandardCharsets.UTF_8));
        server.expect(requestTo("https://api-m.sandbox.paypal.com/v1/oauth2/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic " + basicCredentials))
                .andExpect(content().string("grant_type=client_credentials"))
                .andRespond(withSuccess(
                        "{\"access_token\":\"sandbox-token\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api-m.sandbox.paypal.com/v1/catalogs/products?page=1&page_size=20"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer sandbox-token"))
                .andRespond(withSuccess(
                        "{\"products\":[{\"id\":\"P-1\",\"name\":\"Laptop\"}]}",
                        MediaType.APPLICATION_JSON));
        PayPalCatalogService service = new PayPalCatalogService(
                "client-id", "client-secret", "sandbox", clientBuilder);

        var products = service.listProducts(1, 20);

        assertEquals("P-1", products.path("products").get(0).path("id").asText());
        assertEquals("Laptop", products.path("products").get(0).path("name").asText());
        server.verify();
    }
}