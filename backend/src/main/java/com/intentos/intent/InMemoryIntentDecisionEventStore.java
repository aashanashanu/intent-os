package com.intentos.intent;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "intentos.elastic.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryIntentDecisionEventStore implements IntentDecisionEventStore {
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<IntentDecisionEvent>> events = new ConcurrentHashMap<>();

    @Override
    public IntentDecisionEvent append(IntentDecisionEvent event) {
        events.computeIfAbsent(event.intentId(), ignored -> new CopyOnWriteArrayList<>()).add(event);
        return event;
    }

    @Override
    public List<IntentDecisionEvent> findAllByIntentId(String intentId) {
        return events.getOrDefault(intentId, new CopyOnWriteArrayList<>()).stream()
                .sorted(Comparator.comparing(IntentDecisionEvent::occurredAt).thenComparing(IntentDecisionEvent::eventId))
                .toList();
    }
}
