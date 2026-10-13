package com.intentos.intent;

import java.util.Optional;

public interface IntentLanguageModel {
    IntentExtractionResult extract(String naturalLanguageRequest);

    default Optional<String> generateAdvisory(IntentModelTask task, String instructions, String input) {
        return Optional.empty();
    }
}
