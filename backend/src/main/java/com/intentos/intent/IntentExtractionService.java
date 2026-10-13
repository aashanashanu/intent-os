package com.intentos.intent;

import org.springframework.stereotype.Service;

@Service
public class IntentExtractionService {
    private final IntentLanguageModel languageModel;

    public IntentExtractionService(IntentLanguageModel languageModel) {
        this.languageModel = languageModel;
    }

    public IntentExtractionResult extract(String request) {
        if (request == null || request.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "A natural-language purchase request is required");
        }
        return languageModel.extract(request.trim());
    }
}
