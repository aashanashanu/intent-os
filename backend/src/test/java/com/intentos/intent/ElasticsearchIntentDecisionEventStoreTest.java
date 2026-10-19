package com.intentos.intent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
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

class ElasticsearchIntentDecisionEventStoreTest {
    @Test
        void retrievesPaginatedEventsForDeterministicMetrics() {
        RestClient.Builder clientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
        server.expect(requestTo("https://elastic.test/intent-event-v1/_search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"query":{"term":{"intentId.keyword":"INT-1"}},"sort":[{"occurredAt":{"order":"asc"}},{"eventId.keyword":{"order":"asc"}}],"size":500}
                        """))
                .andRespond(withSuccess("""
                        {"hits":{"hits":[{"sort":["2026-10-13T00:00:01Z","EVT-1"],"_source":{
                          "eventId":"EVT-1","intentId":"INT-1","proposalId":"PROP-1",
                          "runId":"RUN-1","revisionId":"RUN-1-R1","eventType":"SHOPPING_VALIDATION",
                          "decision":"BLOCK","calculatedTotal":1249,
                          "occurredAt":"2026-10-13T00:00:01Z","message":"Budget exceeded",
                          "violations":[{"type":"BUDGET_EXCEEDED","item":"laptop"}]
                        }}]}}
                        """, MediaType.APPLICATION_JSON));
        ElasticsearchIntentDecisionEventStore store = new ElasticsearchIntentDecisionEventStore(
                "https://elastic.test", "encoded-test-key", new ObjectMapper(), clientBuilder);

        var events = store.findMetricEventsByIntentId("INT-1");

        assertEquals(1, events.size());
        assertEquals("PROP-1", events.get(0).proposalId());
        assertEquals("RUN-1", events.get(0).runId());
        assertEquals("SHOPPING_VALIDATION", events.get(0).eventType());
        assertEquals(1, events.get(0).violationCount());
        assertEquals(0, new BigDecimal("1249.0").compareTo(events.get(0).calculatedTotal()));
        assertEquals(Instant.parse("2026-10-13T00:00:01Z"), events.get(0).occurredAt());
        server.verify();
    }
}
