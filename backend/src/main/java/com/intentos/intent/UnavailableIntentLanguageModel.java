package com.intentos.intent;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnProperty(name = "intentos.llm.enabled", havingValue = "false", matchIfMissing = true)
public class UnavailableIntentLanguageModel implements IntentLanguageModel {
    @Override
    public IntentExtractionResult extract(String naturalLanguageRequest) {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Intent extraction is unavailable: enable Amazon Bedrock and configure its model access");
    }
}
