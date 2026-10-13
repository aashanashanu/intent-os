package com.intentos.intent;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/intents/extract")
public class IntentExtractionController {
    private final IntentExtractionService extractionService;

    public IntentExtractionController(IntentExtractionService extractionService) {
        this.extractionService = extractionService;
    }

    @PostMapping
    public IntentExtractionResult extract(@RequestBody IntentExtractionRequest request) {
        if (request == null || request.naturalLanguageRequest() == null
                || request.naturalLanguageRequest().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A natural-language purchase request is required");
        }
        return extractionService.extract(request.naturalLanguageRequest());
    }
}
