package com.intentos.intent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/intents")
public class IntentController {
    private final IntentStore intentStore;
    private final IntentAccessService intentAccessService;

    public IntentController(IntentStore intentStore, IntentAccessService intentAccessService) {
        this.intentStore = intentStore;
        this.intentAccessService = intentAccessService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IntentContract create(@RequestBody CreateIntentRequest request, HttpServletRequest servletRequest) {
        Object authenticatedUserId = servletRequest.getAttribute("paypalUserId");
        validate(request, authenticatedUserId instanceof String);
        IntentContract contract = new IntentContract(
                "INT-" + UUID.randomUUID(),
                authenticatedUserId instanceof String userId ? userId : request.userId().trim(),
                Instant.now(),
                request.naturalLanguageRequest().trim(),
                request.goal().trim(),
                request.maxAmount(),
                request.currency().trim().toUpperCase(),
                request.allowedCategories(),
                request.requiredAttributes(),
                request.preferredAttributes(),
                request.prohibitedItems(),
                Boolean.TRUE.equals(request.allowRecurringPayments()),
                Boolean.TRUE.equals(request.allowSubscriptions()),
                Boolean.TRUE.equals(request.allowUnrequestedAddons()),
                request.maximumTransactionAmount(),
                request.merchantRestrictions(),
                request.maximumQuantities(),
                request.expiration(),
                BigDecimal.ONE,
                "BUYER_APPROVAL");
        return intentStore.save(contract);
    }

    @GetMapping
    public List<IntentContract> list(HttpServletRequest request) {
        Object authenticatedUserId = request.getAttribute("paypalUserId");
        return authenticatedUserId instanceof String userId ? intentStore.findAllByUserId(userId) : intentStore.findAll();
    }

    @GetMapping("/{intentId}")
    public IntentContract get(@PathVariable String intentId, HttpServletRequest request) {
        return intentAccessService.findAccessible(intentId, request)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Intent not found"));
    }

    private static void validate(CreateIntentRequest request, boolean authenticated) {
        if (request == null || (!authenticated && blank(request.userId())) || blank(request.naturalLanguageRequest()) || blank(request.goal())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User, request, and goal are required");
        }
        if (request.maxAmount() == null || request.maxAmount().signum() <= 0 || blank(request.currency())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A positive budget and currency are required");
        }
        if (request.maximumTransactionAmount() != null && request.maximumTransactionAmount().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maximum transaction amount must be positive");
        }
        if (request.maximumQuantities() != null && request.maximumQuantities().entrySet().stream()
                .anyMatch(entry -> entry.getKey().isBlank() || entry.getValue() == null || entry.getValue() <= 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Per-item quantity limits must have a name and a positive quantity");
        }
        if (request.expiration() != null && !request.expiration().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Intent expiration must be in the future");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}