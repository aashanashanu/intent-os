package com.intentos.paypal;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "intentos.elastic.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryAgentReceiptStore implements AgentReceiptStore {
    private final ConcurrentHashMap<String, AgentReceipt> receiptsByOrderId = new ConcurrentHashMap<>();

    @Override
    public boolean saveIfAbsent(AgentReceipt receipt) {
        return receiptsByOrderId.putIfAbsent(receipt.paypalOrderId(), receipt) == null;
    }

    @Override
    public List<AgentReceipt> findAllByIntentId(String intentId) {
        return receiptsByOrderId.values().stream()
                .filter(receipt -> receipt.intentId().equals(intentId))
                .sorted(Comparator.comparing(AgentReceipt::createdAt))
                .toList();
    }

    @Override
    public List<AgentReceipt> search(String intentId, String query, String status, int limit) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        String normalizedStatus = status == null ? "" : status.trim().toUpperCase(java.util.Locale.ROOT);
        return findAllByIntentId(intentId).stream()
                .filter(receipt -> normalizedStatus.isEmpty()
                        || normalizedStatus.equalsIgnoreCase(receipt.captureStatus())
                    || normalizedStatus.equalsIgnoreCase(receipt.finalDecision().name())
                    || receipt.activityTimeline().stream().anyMatch(event -> normalizedStatus.equalsIgnoreCase(event.decision().name()))
                    || receipt.violations().stream().anyMatch(violation -> normalizedStatus.equalsIgnoreCase(violation.type())))
                .filter(receipt -> normalizedQuery.isEmpty() || lexicalScore(receipt, normalizedQuery) > 0)
                .sorted(Comparator.comparingInt((AgentReceipt receipt) -> lexicalScore(receipt, normalizedQuery)).reversed()
                    .thenComparing(AgentReceipt::createdAt, Comparator.reverseOrder())
                    .thenComparing(AgentReceipt::receiptId))
                .limit(limit)
                .toList();
    }

            private static int lexicalScore(AgentReceipt receipt, String query) {
            if (query.isBlank()) return 0;
            String text = searchableText(receipt);
            return java.util.Arrays.stream(query.split("\\W+"))
                .filter(token -> token.length() > 1)
                .mapToInt(token -> text.contains(token) ? 1 : 0)
                .sum();
            }

    private static String searchableText(AgentReceipt receipt) {
        StringBuilder text = new StringBuilder()
                .append(receipt.intent().naturalLanguageRequest()).append(' ')
                .append(receipt.intent().goal()).append(' ')
                .append(receipt.paypalOrderId()).append(' ')
                .append(receipt.paypalCaptureId()).append(' ')
                .append(receipt.captureStatus()).append(' ');
        receipt.proposalHistory().forEach(proposal -> proposal.items().forEach(item -> text.append(item.name()).append(' ')));
        receipt.violations().forEach(violation -> text.append(violation.type()).append(' ').append(violation.item()).append(' '));
        receipt.activityTimeline().forEach(event -> text.append(event.message()).append(' ').append(event.eventType()).append(' '));
        return text.toString().toLowerCase(java.util.Locale.ROOT);
    }
}