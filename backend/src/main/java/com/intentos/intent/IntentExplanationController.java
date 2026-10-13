package com.intentos.intent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/intents/{intentId}/explanation")
public class IntentExplanationController {
    private static final String EXPLANATION_INSTRUCTIONS = """
            Explain the recorded IntentOS decision to the user in concise plain language.
            Use only the supplied intent and event evidence. Distinguish attempted, repaired, approved, and paid states.
            Never claim payment occurred unless an authoritative payment event explicitly records it.
            Do not make or revise policy decisions. If evidence is incomplete, say what is unknown.
            """;

    private final IntentAccessService accessService;
    private final IntentDecisionEventStore eventStore;
    private final IntentLanguageModel languageModel;
    private final ObjectMapper objectMapper;

    public IntentExplanationController(
            IntentAccessService accessService,
            IntentDecisionEventStore eventStore,
            IntentLanguageModel languageModel,
            ObjectMapper objectMapper) {
        this.accessService = accessService;
        this.eventStore = eventStore;
        this.languageModel = languageModel;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public Map<String, Object> explain(@PathVariable String intentId, HttpServletRequest request) {
        IntentContract intent = accessService.findAccessible(intentId, request)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Intent not found"));
        List<IntentDecisionEvent> events = eventStore.findAllByIntentId(intentId);
        if (events.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No recorded decision events are available to explain");
        }
        String evidence;
        try {
            evidence = objectMapper.writeValueAsString(Map.of(
                    "request", intent.naturalLanguageRequest(),
                    "goal", intent.goal(),
                    "budget", Map.of("amount", intent.maxAmount(), "currency", intent.currency()),
                    "events", events.stream().skip(Math.max(0, events.size() - 50)).toList()));
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not prepare decision evidence", exception);
        }
        String explanation = languageModel.generateAdvisory(IntentModelTask.EXPLANATION,
                EXPLANATION_INSTRUCTIONS, evidence)
                .filter(text -> !text.isBlank())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Decision explanation model is not configured"));
        return Map.of("intentId", intentId, "explanation", explanation, "eventCount", events.size());
    }
}
