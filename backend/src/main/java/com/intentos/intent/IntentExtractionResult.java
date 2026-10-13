package com.intentos.intent;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record IntentExtractionResult(
        String naturalLanguageRequest,
        String goal,
        BigDecimal maxAmount,
        String currency,
        Set<String> allowedCategories,
        Map<String, String> requiredAttributes,
        boolean allowRecurringPayments,
        boolean allowSubscriptions,
        boolean allowUnrequestedAddons,
        BigDecimal confidence,
        boolean needsClarification,
        List<String> clarificationQuestions) {
}
