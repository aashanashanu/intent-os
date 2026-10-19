package com.intentos.intent;

import java.util.Optional;
import java.util.List;

public interface IntentLanguageModel {
    IntentExtractionResult extract(String naturalLanguageRequest);

    default Optional<String> generateAdvisory(IntentModelTask task, String instructions, String input) {
        return Optional.empty();
    }

    default Optional<ShoppingSelection> selectShopping(IntentContract intent, List<ShoppingCandidate> candidates) {
        return Optional.empty();
    }
}
