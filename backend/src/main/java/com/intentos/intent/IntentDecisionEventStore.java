package com.intentos.intent;

import java.util.List;

public interface IntentDecisionEventStore {
    IntentDecisionEvent append(IntentDecisionEvent event);

    List<IntentDecisionEvent> findAllByIntentId(String intentId);

    default List<DecisionMetricEvent> findMetricEventsByIntentId(String intentId) {
        return findAllByIntentId(intentId).stream()
                .map(event -> new DecisionMetricEvent(event.proposalId(), event.eventType(), event.decision(),
                        event.calculatedTotal(), event.occurredAt(), event.message()))
                .toList();
    }
}
