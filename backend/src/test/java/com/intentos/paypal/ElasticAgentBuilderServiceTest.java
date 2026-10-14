package com.intentos.paypal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ElasticAgentBuilderServiceTest {
    @Test
    void sendsOnlyGroundedEvidenceToConfiguredReceiptAnalyst() {
        RestClient.Builder clientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
        server.expect(requestTo("https://kibana.test/api/agent_builder/converse"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "ApiKey encoded-test-key"))
                .andExpect(header("kbn-xsrf", "true"))
                .andExpect(content().json("""
                        {"agent_id":"receipt-analyst","input":"Use these receipts only: RCPT-1"}
                        """))
                .andRespond(withSuccess("""
                        {"response":{"message":"Receipt RCPT-1 shows a completed Sandbox capture."}}
                        """, MediaType.APPLICATION_JSON));
        ElasticAgentBuilderService service = new ElasticAgentBuilderService(
                "https://kibana.test", "encoded-test-key", "receipt-analyst", clientBuilder, new ObjectMapper());

        assertEquals("Receipt RCPT-1 shows a completed Sandbox capture.",
                service.answer("Use these receipts only: RCPT-1"));
        server.verify();
    }

        @Test
        void extractsAnswerFromAgentBuilderRoundResponse() {
                RestClient.Builder clientBuilder = RestClient.builder();
                MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
                server.expect(requestTo("https://kibana.test/api/agent_builder/converse"))
                                .andRespond(withSuccess("""
                                                {"rounds":[{"response":{"message":{"content":"Receipt RCPT-1 records an approved Sandbox capture."}}}]}
                                                """, MediaType.APPLICATION_JSON));
                ElasticAgentBuilderService service = new ElasticAgentBuilderService(
                                "https://kibana.test", "encoded-test-key", "receipt-analyst", clientBuilder, new ObjectMapper());

                assertEquals("Receipt RCPT-1 records an approved Sandbox capture.",
                                service.answer("grounded receipt input"));
                server.verify();
        }
}