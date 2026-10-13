package com.intentos.intent;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/intents/{intentId}/events")
public class IntentDecisionEventController {
    private final IntentAccessService intentAccessService;
    private final IntentDecisionEventStore eventStore;

    public IntentDecisionEventController(
            IntentAccessService intentAccessService,
            IntentDecisionEventStore eventStore) {
        this.intentAccessService = intentAccessService;
        this.eventStore = eventStore;
    }

    @GetMapping
    public List<IntentDecisionEvent> list(@PathVariable String intentId, HttpServletRequest request) {
        intentAccessService.findAccessible(intentId, request)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Intent not found"));
        return eventStore.findAllByIntentId(intentId);
    }
}
