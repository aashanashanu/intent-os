package com.intentos.intent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

public record IntentContract(
        String intentId,
        String userId,
        Instant createdAt,
        String naturalLanguageRequest,
        String goal,
        BigDecimal maxAmount,
        String currency,
        Set<String> allowedCategories,
        Map<String, String> requiredAttributes,
        Map<String, String> preferredAttributes,
        Set<String> prohibitedItems,
        boolean allowRecurringPayments,
        boolean allowSubscriptions,
        boolean allowUnrequestedAddons,
        BigDecimal maximumTransactionAmount,
        Set<String> merchantRestrictions,
        Map<String, Integer> maximumQuantities,
        Instant expiration,
        BigDecimal confidence,
        String approvalPolicy) {

    public IntentContract {
        allowedCategories = allowedCategories == null ? Set.of() : Set.copyOf(allowedCategories);
        requiredAttributes = requiredAttributes == null ? Map.of() : Map.copyOf(requiredAttributes);
        preferredAttributes = preferredAttributes == null ? Map.of() : Map.copyOf(preferredAttributes);
        prohibitedItems = prohibitedItems == null ? Set.of() : Set.copyOf(prohibitedItems);
        merchantRestrictions = merchantRestrictions == null ? Set.of() : Set.copyOf(merchantRestrictions);
        maximumQuantities = maximumQuantities == null ? Map.of() : Map.copyOf(maximumQuantities);
    }
}