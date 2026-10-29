package com.intentos.paypal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ElasticsearchAgentReceiptStoreTest {
    @org.junit.jupiter.api.Test
    void writesReceiptDatesUsingTheApplicationMapper() {
        RestClient.Builder clientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
        server.expect(requestTo("https://elastic.test/agent-receipt-v1"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://elastic.test/agent-receipt-v1/_create/ORDER-1"))
                .andExpect(content().json("""
                        {"createdAt":"2026-10-28T23:54:41Z"}
                        """))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        ElasticsearchAgentReceiptStore store = new ElasticsearchAgentReceiptStore(
                "https://elastic.test", "test-key", "searchText", mapper, clientBuilder);
        AgentReceipt receipt = new AgentReceipt("RCPT-1", "INT-1", null, java.util.List.of(),
                java.util.List.of(), java.util.List.of(), com.intentos.validation.ValidationDecision.APPROVE,
                new java.math.BigDecimal("854.40"), java.math.BigDecimal.ZERO, "USD", "ORDER-1", "CAP-1",
                "COMPLETED", "COMPLETED", "WH-1", java.time.Instant.parse("2026-10-28T23:54:41Z"),
                java.util.List.of(), "receipt");

        assertTrue(store.saveIfAbsent(receipt));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "laptop"})
    void searchesEmptyReceiptIndexWithDateMappingAndUnmappedSort(String query) {
        RestClient.Builder clientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
        server.expect(requestTo("https://elastic.test/agent-receipt-v1"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().json("""
                        {"mappings":{"properties":{"searchText":{"type":"semantic_text"},"createdAt":{"type":"date"}}}}
                        """))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://elastic.test/agent-receipt-v1/_search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json(query.isEmpty() ? """
                        {"sort":[{"createdAt":{"order":"desc","unmapped_type":"date"}}]}
                        """ : """
                        {"sort":[{"_score":{"order":"desc"}},{"createdAt":{"order":"desc","unmapped_type":"date"}}]}
                        """))
                .andRespond(withSuccess("{\"hits\":{\"hits\":[]}}", MediaType.APPLICATION_JSON));
        ElasticsearchAgentReceiptStore store = new ElasticsearchAgentReceiptStore(
                "https://elastic.test", "test-key", "searchText", new ObjectMapper(), clientBuilder);

        assertTrue(query.isEmpty() ? store.findAllByIntentId("INT-1").isEmpty()
                : store.search("INT-1", query, "", 25).isEmpty());

        server.verify();
    }
}