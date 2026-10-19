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
    void retrievesOrderedProposalSummariesThroughEsqlForDeterministicMetrics() {
        RestClient.Builder clientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
        server.expect(requestTo("https://elastic.test/_query"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"query":"FROM intent-event-v1 | WHERE intentId.keyword == \\"INT-1\\" | KEEP proposalId, runId, revisionId, eventType, decision, calculatedTotal, occurredAt, message, violations | SORT occurredAt ASC | LIMIT 10000"}
                        """))
                .andRespond(withSuccess("""
                        {
                          "columns":[
                            {"name":"proposalId","type":"keyword"},
                            {"name":"runId","type":"keyword"},
                            {"name":"revisionId","type":"keyword"},
                            {"name":"eventType","type":"keyword"},
                            {"name":"decision","type":"keyword"},
                            {"name":"calculatedTotal","type":"double"},
                            {"name":"occurredAt","type":"date"},
                                                                                                                {"name":"message","type":"keyword"},
                                                                                                                {"name":"violations","type":"keyword"}
                          ],
                                                                                                        "values":[["PROP-1","RUN-1","RUN-1-R1","SHOPPING_VALIDATION","BLOCK",1249,"2026-10-13T00:00:01Z","Budget exceeded",["BUDGET_EXCEEDED"]]]
                        }
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
