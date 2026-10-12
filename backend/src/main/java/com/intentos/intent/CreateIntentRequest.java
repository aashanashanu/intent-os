package com.intentos.intent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

public record CreateIntentRequest(
        String userId,
        String naturalLanguageRequest,
        String goal,
        BigDecimal maxAmount,
        String currency,
        Set<String> allowedCategories,
        Map<String, String> requiredAttributes,
        Map<String, String> preferredAttributes,
        Set<String> prohibitedItems,
        Boolean allowRecurringPayments,
        Boolean allowSubscriptions,
        Boolean allowUnrequestedAddons,
        BigDecimal maximumTransactionAmount,
        Set<String> merchantRestrictions,
        Map<String, Integer> maximumQuantities,
        Instant expiration) {
}