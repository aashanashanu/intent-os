package com.intentos.merchant;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ElasticsearchMerchantQuoteStoreTest {
    @Test
    void createsImmutableQuoteDocumentAndReadsItBack() {
        RestClient.Builder clientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
        server.expect(requestTo("https://elastic.test/merchant-quote-v1/_create/MQ-1"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().json("{\"quoteId\":\"MQ-1\",\"intentId\":\"INT-1\"}"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://elastic.test/merchant-quote-v1/_doc/MQ-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"found":true,"_source":{
                          "quoteId":"MQ-1","intentId":"INT-1","source":"simulated-merchant",
                          "simulation":true,"merchantId":"store","catalogVersion":"1",
                          "catalogFingerprint":"hash","currency":"USD","status":"READY",
                          "request":{"lines":[],"country":"US","region":"CA","shippingMethod":"standard"},
                          "lines":[],"subtotal":10.00,"tax":1.00,"shipping":2.00,"total":13.00,
                          "createdAt":"2026-10-19T10:00:00Z","expiresAt":"2026-10-19T10:05:00Z",
                          "missingEvidence":[],"disclosure":"simulated"
                        }}
                        """, MediaType.APPLICATION_JSON));
        ElasticsearchMerchantQuoteStore store = new ElasticsearchMerchantQuoteStore(
                "https://elastic.test", "encoded-test-key", new ObjectMapper().findAndRegisterModules(), clientBuilder);
        MerchantQuote quote = new MerchantQuote("MQ-1", "INT-1", "simulated-merchant", true, "store", "1",
                "hash", "USD", MerchantQuote.Status.READY,
                new MerchantQuoteRequest(List.of(), "US", "CA", "standard"), List.of(), new BigDecimal("10.00"),
                new BigDecimal("1.00"), new BigDecimal("2.00"), new BigDecimal("13.00"),
                Instant.parse("2026-10-19T10:00:00Z"), Instant.parse("2026-10-19T10:05:00Z"), List.of(), "simulated");

        assertEquals(quote, store.save(quote));
        MerchantQuote restored = store.find("MQ-1").orElseThrow();
        assertEquals(quote.quoteId(), restored.quoteId());
        assertEquals(quote.intentId(), restored.intentId());
        assertEquals(0, quote.total().compareTo(restored.total()));
        server.verify();
    }
}
