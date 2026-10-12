package com.intentos.intent;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "intentos.elastic.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryIntentStore implements IntentStore {
    private final ConcurrentMap<String, IntentContract> contracts = new ConcurrentHashMap<>();

    @Override
    public IntentContract save(IntentContract contract) {
        contracts.put(contract.intentId(), contract);
        return contract;
    }

    @Override
    public Optional<IntentContract> findById(String intentId) {
        return Optional.ofNullable(contracts.get(intentId));
    }

    @Override
    public Optional<IntentContract> findByIdAndUserId(String intentId, String userId) {
        return findById(intentId).filter(contract -> contract.userId().equals(userId));
    }

    @Override
    public List<IntentContract> findAll() {
        return contracts.values().stream()
                .sorted(Comparator.comparing(IntentContract::createdAt).reversed())
                .toList();
    }

    @Override
    public List<IntentContract> findAllByUserId(String userId) {
        return findAll().stream().filter(contract -> contract.userId().equals(userId)).toList();
    }
}