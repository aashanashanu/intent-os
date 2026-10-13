package com.intentos.intent;

import java.util.List;

public interface IntentDecisionEventStore {
    IntentDecisionEvent append(IntentDecisionEvent event);

    List<IntentDecisionEvent> findAllByIntentId(String intentId);
}
