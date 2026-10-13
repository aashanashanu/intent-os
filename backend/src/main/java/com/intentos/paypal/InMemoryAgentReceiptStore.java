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
}