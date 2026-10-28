package com.intentos.paypal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
public class AnalystMcpConfiguration {
    @Bean
    WebMvcStatelessServerTransport analystTransport() {
        return WebMvcStatelessServerTransport.builder().messageEndpoint("/api/analyst/mcp").build();
    }

    @Bean
    RouterFunction<ServerResponse> analystMcpRoutes(WebMvcStatelessServerTransport transport) {
        return transport.getRouterFunction();
    }

    @Bean(destroyMethod = "close")
    McpStatelessSyncServer analystMcpServer(WebMvcStatelessServerTransport transport,
            AnalystToolScopeService scopes, ObjectMapper mapper) {
        var server = McpServer.sync(transport).serverInfo("intentos-receipt-tools", "1.0.0")
                .requestTimeout(Duration.ofSeconds(30))
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build());
        for (String tool : List.of("search_receipts", "get_receipt", "get_decisions", "get_metrics")) {
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("capability", Map.of("type", "string", "description", "Short-lived capability from the authorized IntentOS request. Never change it."));
            List<String> required = new ArrayList<>(List.of("capability"));
            if (tool.equals("search_receipts")) {
                properties.put("query", Map.of("type", "string", "maxLength", 500));
                properties.put("status", Map.of("type", "string", "maxLength", 32));
                properties.put("limit", Map.of("type", "integer", "minimum", 1, "maximum", 20));
            }
            if (tool.equals("get_receipt")) {
                properties.put("receiptId", Map.of("type", "string", "maxLength", 200));
                required.add("receiptId");
            }
            var schema = new McpSchema.JsonSchema("object", properties, required, false, null, null);
            var definition = new McpSchema.Tool(tool, tool, description(tool), schema, null, null, null);
            server.toolCall(definition, (context, request) -> {
                try {
                    Object result = scopes.execute(tool, mapper.valueToTree(request.arguments()));
                    String json = mapper.writeValueAsString(result);
                    return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(json)), false);
                } catch (ResponseStatusException exception) {
                    return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent("Tool access or arguments rejected")), true);
                } catch (JsonProcessingException exception) {
                    return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent("Tool evidence could not be encoded")), true);
                } catch (RuntimeException exception) {
                    return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent("Tool evidence is unavailable")), true);
                }
            });
        }
        return server.build();
    }

    private static String description(String tool) {
        return switch (tool) {
            case "search_receipts" -> "Search captured receipts only within the capability's authorized intent. An empty query lists receipts. Returns at most 20.";
            case "get_receipt" -> "Read one receipt by ID within the capability's authorized intent, including intent, carts, policy findings and payment timeline.";
            case "get_decisions" -> "Read up to 50 decision events for the authorized intent, including unpaid blocked or held proposals. These are not payment receipts.";
            case "get_metrics" -> "Read deterministic backend metrics for the authorized intent. Use these numbers without recalculating financial metrics.";
            default -> throw new IllegalArgumentException("Unknown tool");
        };
    }
}