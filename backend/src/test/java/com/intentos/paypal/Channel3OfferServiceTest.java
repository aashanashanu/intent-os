package com.intentos.paypal;

import com.intentos.intent.IntentContract;
import com.intentos.intent.OfferEvidence;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class Channel3OfferServiceTest {
    @Test
    void searchesWithIntentGoalBudgetAndRequiredAttributesAndNormalizesRetailOffers() {
        RestClient.Builder clientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
        server.expect(requestTo("https://api.trychannel3.com/v1/search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", "channel3-test-key"))
                .andExpect(content().json("""
                        {
                          "query":"Development laptop ram 16GB",
                          "limit":20,
                          "currency":"USD",
                          "filters":{"price":{"max_price":1000},"availability":["InStock"]}
                        }
                        """))
                .andRespond(withSuccess("""
                        {"products":[{
                          "id":"PRODUCT-1",
                          "title":"Developer Laptop",
                          "description":"A laptop",
                          "category":{"slug":"laptops","title":"Laptops"},
                          "structured_attributes":{"ram":"32GB","storage":"1TB"},
                          "offers":[{
                            "url":"https://shop.example/laptop",
                            "domain":"shop.example",
                            "price":{"amount":899.00,"currency":"USD"},
                            "availability":"InStock",
                            "condition":"new"
                          },{
                            "url":"https://other.example/laptop",
                            "domain":"other.example",
                            "price":{"amount":799,"currency":"USD"},
                            "availability":"OutOfStock"
                          }]
                        }]}
                        """, MediaType.APPLICATION_JSON));
        Channel3OfferService service = new Channel3OfferService("channel3-test-key", clientBuilder);

        var offers = service.search(intent());

        assertEquals(1, offers.size());
        var offer = offers.get(0);
        assertEquals("PRODUCT-1", offer.productId());
        assertEquals("Developer Laptop", offer.productName());
        assertEquals("Laptops", offer.category());
        assertEquals("shop.example", offer.retailer());
        assertEquals(0, new BigDecimal("899.00").compareTo(offer.price()));
        assertEquals("USD", offer.currency());
        assertEquals("32GB", offer.attributes().get("ram"));
        assertNotNull(offer.retrievedAt());
        assertEquals("channel3", offer.evidence().source());
        server.verify();
    }

    @Test
    void refusesCheckoutWhenOfferPriceChangesAtLookup() {
        RestClient.Builder clientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
        server.expect(requestTo("https://api.trychannel3.com/v1/lookup"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", "channel3-test-key"))
                .andExpect(content().json("""
                        {"url":"https://shop.example/laptop"}
                        """))
                .andRespond(withSuccess("""
                        {"product":{
                          "id":"PRODUCT-1","title":"Developer Laptop",
                          "offers":[{
                            "url":"https://shop.example/laptop","domain":"shop.example",
                            "price":{"amount":949,"currency":"USD"},"availability":"InStock"
                          }]
                        }}
                        """, MediaType.APPLICATION_JSON));
        Channel3OfferService service = new Channel3OfferService("channel3-test-key", clientBuilder);
        OfferEvidence selected = new OfferEvidence("channel3", "PRODUCT-1", "Developer Laptop",
                "shop.example", "https://shop.example/laptop", new BigDecimal("899"), "USD",
                "InStock", Instant.now());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.refresh(selected));

        assertEquals(409, exception.getStatusCode().value());
        server.verify();
    }

    @Test
    void rejectsOfferThatIsNoLongerInStock() {
        RestClient.Builder clientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
        server.expect(requestTo("https://api.trychannel3.com/v1/lookup"))
                .andRespond(withSuccess("""
                        {"product":{
                          "id":"PRODUCT-1","title":"Developer Laptop",
                          "offers":[{
                            "url":"https://shop.example/laptop","domain":"shop.example",
                            "price":{"amount":899,"currency":"USD"},"availability":"OutOfStock"
                          }]
                        }}
                        """, MediaType.APPLICATION_JSON));
        Channel3OfferService service = new Channel3OfferService("channel3-test-key", clientBuilder);
        OfferEvidence selected = new OfferEvidence("channel3", "PRODUCT-1", "Developer Laptop",
                "shop.example", "https://shop.example/laptop", new BigDecimal("899"), "USD",
                "InStock", Instant.now());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.refresh(selected));

        assertEquals(409, exception.getStatusCode().value());
        server.verify();
    }

    private static IntentContract intent() {
        return new IntentContract("INT-1", "USER-1", Instant.now(), "Development laptop with 16GB RAM",
                "Development laptop", new BigDecimal("1000"), "USD", Set.of(), Map.of("ram", "16GB"),
                Map.of(), Set.of(), false, false, false, null, Set.of(), Map.of(), null,
                BigDecimal.ONE, "BUYER_APPROVAL");
    }
}