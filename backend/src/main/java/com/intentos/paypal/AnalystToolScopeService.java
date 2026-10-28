package com.intentos.paypal;

import com.fasterxml.jackson.databind.JsonNode;
import com.intentos.intent.IntentContract;
import com.intentos.intent.IntentDecisionEvent;
import com.intentos.intent.IntentDecisionEventStore;
import com.intentos.intent.IntentMetricsService;
import com.intentos.intent.IntentStore;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AnalystToolScopeService {
    private static final Set<String> TOOLS = Set.of("search_receipts", "get_receipt", "get_decisions", "get_metrics");
    private final Map<String, Scope> scopes = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;
    private final IntentStore intents;
    private final AgentReceiptStore receipts;
    private final IntentDecisionEventStore events;
    private final IntentMetricsService metrics;

    @Autowired
    public AnalystToolScopeService(IntentStore intents, AgentReceiptStore receipts,
            IntentDecisionEventStore events, IntentMetricsService metrics) {
        this(intents, receipts, events, metrics, Clock.systemUTC());
    }

    AnalystToolScopeService(IntentStore intents, AgentReceiptStore receipts,
            IntentDecisionEventStore events, IntentMetricsService metrics, Clock clock) {
        this.intents = intents;
        this.receipts = receipts;
        this.events = events;
        this.metrics = metrics;
        this.clock = clock;
    }

    public synchronized String issue(IntentContract authorizedIntent) {
        scopes.entrySet().removeIf(entry -> !entry.getValue().expiresAt.isAfter(clock.instant()));
        if (scopes.size() >= 100) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Analyst capacity reached");
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String capability = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        scopes.put(capability, new Scope(authorizedIntent.intentId(), authorizedIntent.userId(),
                clock.instant().plus(Duration.ofMinutes(2))));
        return capability;
    }

    public Object execute(String tool, JsonNode arguments) {
        if (!TOOLS.contains(tool) || arguments == null || !arguments.isObject()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown analyst tool or invalid arguments");
        }
        Set<String> allowed = switch (tool) {
            case "search_receipts" -> Set.of("capability", "query", "status", "limit");
            case "get_receipt" -> Set.of("capability", "receiptId");
            default -> Set.of("capability");
        };
        arguments.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tool argument not allowed");
        });
        Scope scope = scopes.get(arguments.path("capability").asText());
        if (scope == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid analyst capability");
        synchronized (scope) {
            if (scope.revoked || !scope.expiresAt.isAfter(clock.instant()) || scope.attempts >= 8) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Analyst capability expired or exhausted");
            }
            scope.attempts++;
            intents.findByIdAndUserId(scope.intentId, scope.ownerId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Analyst scope no longer authorized"));
            Object result = switch (tool) {
                case "search_receipts" -> {
                    String query = text(arguments, "query", 500);
                    String status = text(arguments, "status", 32);
                    JsonNode limit = arguments.get("limit");
                    if (limit != null && (!limit.isIntegralNumber() || !limit.canConvertToInt()
                            || limit.asInt() < 1 || limit.asInt() > 20)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tool limit must be between 1 and 20");
                    }
                    yield receipts.search(scope.intentId, query, status, limit == null ? 10 : limit.asInt());
                }
                case "get_receipt" -> receipts.findAllByIntentId(scope.intentId).stream()
                        .filter(receipt -> receipt.receiptId().equals(text(arguments, "receiptId", 200)))
                        .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Receipt not found in scope"));
                case "get_decisions" -> events.findAllByIntentId(scope.intentId).stream().limit(50).toList();
                case "get_metrics" -> metrics.calculate(scope.intentId);
                default -> throw new IllegalStateException("Unsupported analyst tool");
            };
            if (result instanceof AgentReceipt receipt) scope.receipts.add(receipt);
            if (result instanceof List<?> records) {
                for (Object record : records) {
                    if (record instanceof AgentReceipt receipt) scope.receipts.add(receipt);
                    if (record instanceof IntentDecisionEvent event) scope.events.add(event);
                }
            }
            scope.calls.add(tool);
            return result;
        }
    }

    public List<String> completedCalls(String capability) {
        Scope scope = scopes.get(capability);
        if (scope == null) return List.of();
        synchronized (scope) {
            return List.copyOf(scope.calls);
        }
    }

    public void revoke(String capability) {
        Scope scope = scopes.remove(capability);
        if (scope != null) {
            synchronized (scope) {
                scope.revoked = true;
            }
        }
    }

    public Evidence evidence(String capability) {
        Scope scope = scopes.get(capability);
        if (scope == null) return new Evidence(List.of(), List.of());
        synchronized (scope) {
            return new Evidence(scope.receipts.stream().distinct().toList(), scope.events.stream().distinct().toList());
        }
    }

    public record Evidence(List<AgentReceipt> receipts, List<IntentDecisionEvent> events) {}

    private static String text(JsonNode arguments, String field, int maximum) {
        JsonNode value = arguments.get(field);
        if (value == null) return "";
        if (!value.isTextual() || value.asText().length() > maximum) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid tool argument");
        }
        return value.asText();
    }

    private static final class Scope {
        private final String intentId;
        private final String ownerId;
        private final Instant expiresAt;
        private final List<String> calls = new ArrayList<>();
        private final List<AgentReceipt> receipts = new ArrayList<>();
        private final List<IntentDecisionEvent> events = new ArrayList<>();
        private int attempts;
        private boolean revoked;

        private Scope(String intentId, String ownerId, Instant expiresAt) {
            this.intentId = intentId;
            this.ownerId = ownerId;
            this.expiresAt = expiresAt;
        }
    }
}