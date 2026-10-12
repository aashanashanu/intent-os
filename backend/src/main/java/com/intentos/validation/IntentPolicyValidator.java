package com.intentos.validation;

import com.intentos.intent.IntentContract;
import com.intentos.intent.TransactionProposal;
import com.intentos.intent.TransactionProposal.Item;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class IntentPolicyValidator {
    private static final BigDecimal MINIMUM_CONFIDENCE = new BigDecimal("0.80");
    private static final Pattern MEASUREMENT = Pattern.compile("^\\s*(\\d+(?:\\.\\d+)?)\\s*([a-zA-Z]+)?\\s*$");
    private final Clock clock;

    public IntentPolicyValidator() {
        this(Clock.systemUTC());
    }

    IntentPolicyValidator(Clock clock) {
        this.clock = clock;
    }

    public ValidationResult validate(IntentContract intent, TransactionProposal proposal) {
        List<PolicyViolation> violations = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        if (!Objects.equals(intent.intentId(), proposal.intentId())) {
            add(violations, "INTENT_MISMATCH", null, intent.intentId(), proposal.intentId());
        }
        if (intent.expiration() != null && !intent.expiration().isAfter(clock.instant())) {
            add(violations, "INTENT_EXPIRED", null, intent.expiration().toString(), clock.instant().toString());
        }
        if (proposal.items().isEmpty()) {
            add(violations, "EMPTY_PROPOSAL", null, "at least one item", "no items");
        }

        BigDecimal calculatedTotal = calculateTotal(proposal, violations);
        if (proposal.total() != null && calculatedTotal.compareTo(proposal.total()) != 0) {
            add(violations, "TOTAL_MISMATCH", null, calculatedTotal.toPlainString(), proposal.total().toPlainString());
        }
        if (intent.currency() != null && proposal.currency() != null
                && !intent.currency().equalsIgnoreCase(proposal.currency())) {
            add(violations, "CURRENCY_MISMATCH", null, intent.currency(), proposal.currency());
        }

        BigDecimal maximum = intent.maxAmount();
        if (intent.maximumTransactionAmount() != null
                && (maximum == null || intent.maximumTransactionAmount().compareTo(maximum) < 0)) {
            maximum = intent.maximumTransactionAmount();
        }
        if (maximum != null && calculatedTotal.compareTo(maximum) > 0) {
            add(violations, "BUDGET_EXCEEDED", null, maximum.toPlainString(), calculatedTotal.toPlainString());
        }

        for (Item item : proposal.items()) {
            validateItem(intent, item, violations, recommendations);
        }

        ValidationDecision decision;
        if (intent.confidence() == null || intent.confidence().compareTo(MINIMUM_CONFIDENCE) < 0) {
            decision = ValidationDecision.HOLD;
        } else if (!violations.isEmpty()) {
            decision = ValidationDecision.BLOCK;
        } else if (!recommendations.isEmpty()) {
            decision = ValidationDecision.MODIFY;
        } else {
            decision = ValidationDecision.APPROVE;
        }

        return new ValidationResult(decision, calculatedTotal, violations, recommendations, explanation(decision));
    }

    private static BigDecimal calculateTotal(TransactionProposal proposal, List<PolicyViolation> violations) {
        BigDecimal itemsTotal = BigDecimal.ZERO;
        for (Item item : proposal.items()) {
            if (item.quantity() <= 0) {
                add(violations, "INVALID_QUANTITY", item.name(), "greater than zero", Integer.toString(item.quantity()));
                continue;
            }
            if (item.unitPrice() == null || item.unitPrice().signum() < 0) {
                add(violations, "INVALID_UNIT_PRICE", item.name(), "zero or greater", String.valueOf(item.unitPrice()));
                continue;
            }
            itemsTotal = itemsTotal.add(item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())));
        }
        return itemsTotal.add(zeroIfNull(proposal.shipping())).add(zeroIfNull(proposal.tax()));
    }

    private static void validateItem(
            IntentContract intent,
            Item item,
            List<PolicyViolation> violations,
            List<String> recommendations) {
        if (intent.prohibitedItems().stream().anyMatch(value -> value.equalsIgnoreCase(item.name()))) {
            add(violations, "PROHIBITED_ITEM", item.name(), "not prohibited", item.name());
        }
        if (!intent.allowedCategories().isEmpty()
                && intent.allowedCategories().stream().noneMatch(value -> value.equalsIgnoreCase(item.category()))) {
            add(violations, "CATEGORY_NOT_ALLOWED", item.name(), String.join(", ", intent.allowedCategories()), item.category());
        }
        if (item.recurring() && !intent.allowRecurringPayments()) {
            add(violations, "RECURRING_PAYMENT", item.name(), "not recurring", "recurring");
        }
        if (item.subscription() && !intent.allowSubscriptions()) {
            add(violations, "SUBSCRIPTION_NOT_ALLOWED", item.name(), "subscription allowed", "subscription");
        }
        if (item.addOn() && !intent.allowUnrequestedAddons()) {
            add(violations, "UNREQUESTED_ADDON", item.name(), "requested item", "add-on");
        }
        if (item.merchant() != null && intent.merchantRestrictions().stream()
                .anyMatch(value -> value.equalsIgnoreCase(item.merchant()))) {
            add(violations, "MERCHANT_RESTRICTED", item.name(), "merchant not restricted", item.merchant());
        }
        Integer maximumQuantity = findQuantityLimit(intent.maximumQuantities(), item.name());
        if (maximumQuantity != null && item.quantity() > maximumQuantity) {
            add(violations, "QUANTITY_EXCEEDED", item.name(), maximumQuantity.toString(), Integer.toString(item.quantity()));
        }

        if (!item.addOn()) {
            for (Map.Entry<String, String> required : intent.requiredAttributes().entrySet()) {
                String actual = findAttribute(item.attributes(), required.getKey());
                if (actual == null || !meetsMinimum(actual, required.getValue())) {
                    add(violations, "REQUIRED_ATTRIBUTE_NOT_MET", item.name(),
                            required.getKey() + ">=" + required.getValue(), actual);
                }
            }
        }
        for (Map.Entry<String, String> preferred : intent.preferredAttributes().entrySet()) {
            String actual = findAttribute(item.attributes(), preferred.getKey());
            if (actual != null && !actual.equalsIgnoreCase(preferred.getValue())) {
                recommendations.add("Prefer " + preferred.getKey() + "=" + preferred.getValue() + " for " + item.name());
            }
        }
    }

    private static String findAttribute(Map<String, String> attributes, String key) {
        return attributes.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(key))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private static Integer findQuantityLimit(Map<String, Integer> limits, String itemName) {
        return limits.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(itemName))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private static boolean meetsMinimum(String actual, String expected) {
        Matcher actualMatch = MEASUREMENT.matcher(actual);
        Matcher expectedMatch = MEASUREMENT.matcher(expected);
        if (!actualMatch.matches() || !expectedMatch.matches()) return actual.equalsIgnoreCase(expected);
        if (!Objects.equals(normalize(actualMatch.group(2)), normalize(expectedMatch.group(2)))) return false;
        return new BigDecimal(actualMatch.group(1)).compareTo(new BigDecimal(expectedMatch.group(1))) >= 0;
    }

    private static String normalize(String unit) {
        return unit == null ? null : unit.toLowerCase();
    }

    private static void add(List<PolicyViolation> violations, String type, String item, String expected, String actual) {
        violations.add(new PolicyViolation(type, item, expected, actual));
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String explanation(ValidationDecision decision) {
        return switch (decision) {
            case APPROVE -> "The proposal satisfies the available intent policies.";
            case MODIFY -> "The proposal meets required policies but should be adjusted to better match preferences.";
            case HOLD -> "Intent confidence is below the approval threshold; user clarification is required.";
            case BLOCK -> "The proposal violates one or more deterministic intent policies.";
        };
    }
}