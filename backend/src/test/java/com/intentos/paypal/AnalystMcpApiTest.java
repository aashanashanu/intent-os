package com.intentos.paypal;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.intentos.intent.IntentStore;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"intentos.elastic.enabled=false", "intentos.llm.enabled=false", "intentos.auth.required=true"})
class AnalystMcpApiTest {
    @Autowired private TestRestTemplate rest;
    @Autowired private AnalystToolScopeService scopes;
    @Autowired private IntentStore intents;

    private String rpc(String method, Map<String, Object> parameters) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));
        headers.set("MCP-Protocol-Version", "2025-06-18");
        var response = rest.postForEntity("/api/analyst/mcp", new HttpEntity<>(
                Map.of("jsonrpc", "2.0", "id", 1, "method", method, "params", parameters), headers), String.class);
        assertTrue(response.getStatusCode().is2xxSuccessful(), () -> response.toString());
        return response.getBody();
    }

    @Test
    void discoversOnlyReadOnlyToolsAndRequiresCapabilityForEvidence() {
        String initialized = rpc("initialize", Map.of("protocolVersion", "2025-06-18", "capabilities", Map.of(),
            "clientInfo", Map.of("name", "test-client", "version", "1.0")));
        assertTrue(initialized.contains("intentos-receipt-tools"));
        String discovered = rpc("tools/list", Map.of());
        assertTrue(discovered.contains("get_metrics"));
        assertFalse(discovered.contains("capture_payment"));
        String denied = rpc("tools/call", Map.of("name", "get_decisions", "arguments", Map.of("capability", "forged")));
        assertTrue(denied.contains("\"isError\":true"), denied);
        var intent = new com.intentos.intent.IntentContract("INT-MCP", "OWNER", Instant.now(), "Buy laptop", "Laptop",
                BigDecimal.TEN, "USD", Set.of("laptop"), Map.of(), Map.of(), Set.of(), false, false, false,
                null, Set.of(), Map.of(), null, BigDecimal.ONE, "BUYER_APPROVAL");
        intents.save(intent);
        String capability = scopes.issue(intent);
        try {
            String result = rpc("tools/call", Map.of("name", "get_decisions", "arguments", Map.of("capability", capability)));
            assertTrue(result.contains("\"isError\":false"), result);
            assertTrue(scopes.completedCalls(capability).contains("get_decisions"));
            String metrics = rpc("tools/call", Map.of("name", "get_metrics", "arguments", Map.of("capability", capability)));
            assertTrue(metrics.contains("\"isError\":false"), metrics);
            String search = rpc("tools/call", Map.of("name", "search_receipts", "arguments", Map.of("capability", capability, "query", "", "limit", 10)));
            assertTrue(search.contains("\"isError\":false"), search);
            String override = rpc("tools/call", Map.of("name", "get_decisions", "arguments", Map.of("capability", capability, "intentId", "FOREIGN")));
            assertTrue(override.contains("\"isError\":true") || override.contains("\"error\":"), override);
        } finally {
            scopes.revoke(capability);
        }
    }
}