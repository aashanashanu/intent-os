package com.intentos.validation;

import com.intentos.intent.IntentContract;
import com.intentos.intent.TransactionProposal;
import com.intentos.intent.TransactionProposal.Item;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntentPolicyValidatorTest {
    private final IntentPolicyValidator validator = new IntentPolicyValidator();

    @Test
    void blocksLaptopProposalForBudgetAddonAndRecurringViolations() {
        IntentContract intent = intent(new BigDecimal("1000"), new BigDecimal("0.94"), Map.of("ram", "16GB"), Map.of());
        TransactionProposal proposal = proposal(
                item("Developer Laptop", "899", false, false, false, Map.of("ram", "32GB")),
                item("Extended Warranty", "199", false, false, true, Map.of()),
                item("Antivirus Subscription", "79", true, true, true, Map.of()));

        ValidationResult result = validator.validate(intent, proposal);

        assertEquals(ValidationDecision.BLOCK, result.decision());
        assertEquals(new BigDecimal("1249"), result.calculatedTotal());
        assertTrue(hasViolation(result, "BUDGET_EXCEEDED"));
        assertTrue(hasViolation(result, "UNREQUESTED_ADDON"));
        assertTrue(hasViolation(result, "RECURRING_PAYMENT"));
        assertTrue(hasViolation(result, "SUBSCRIPTION_NOT_ALLOWED"));
        assertTrue(!hasViolation(result, "REQUIRED_ATTRIBUTE_NOT_MET"));
    }

    @Test
    void approvesCompliantProposalAndRecomputesTotal() {
        IntentContract intent = intent(new BigDecimal("1000"), new BigDecimal("0.94"), Map.of("ram", "16GB"), Map.of());
        ValidationResult result = validator.validate(intent,
                proposal(item("Developer Laptop", "899", false, false, false, Map.of("ram", "32GB"))));

        assertEquals(ValidationDecision.APPROVE, result.decision());
        assertEquals(new BigDecimal("971"), result.calculatedTotal());
        assertTrue(result.violations().isEmpty());
    }

    @Test
    void holdsWhenIntentConfidenceNeedsClarification() {
        IntentContract intent = intent(new BigDecimal("1000"), new BigDecimal("0.62"), Map.of(), Map.of());
        ValidationResult result = validator.validate(intent,
                proposal(item("Developer Laptop", "899", false, false, false, Map.of())));

        assertEquals(ValidationDecision.HOLD, result.decision());
    }

    @Test
    void blocksProposalWhenTaxOrShippingIsUnknown() {
        IntentContract intent = intent(new BigDecimal("1000"), new BigDecimal("0.94"), Map.of(), Map.of());
        TransactionProposal proposal = new TransactionProposal("PROP-1", "INT-1", "USD",
                List.of(item("Developer Laptop", "899", false, false, false, Map.of())),
                null, null, new BigDecimal("899"));

        ValidationResult result = validator.validate(intent, proposal);

        assertEquals(ValidationDecision.BLOCK, result.decision());
        assertTrue(hasViolation(result, "MISSING_TAX"));
        assertTrue(hasViolation(result, "MISSING_SHIPPING"));
    }

    @Test
    void blocksNegativeTaxOrShipping() {
        IntentContract intent = intent(new BigDecimal("1000"), new BigDecimal("0.94"), Map.of(), Map.of());
        TransactionProposal proposal = new TransactionProposal("PROP-1", "INT-1", "USD",
                List.of(item("Developer Laptop", "899", false, false, false, Map.of())),
                new BigDecimal("-5"), new BigDecimal("-10"), new BigDecimal("884"));

        ValidationResult result = validator.validate(intent, proposal);

        assertEquals(ValidationDecision.BLOCK, result.decision());
        assertTrue(hasViolation(result, "INVALID_TAX"));
        assertTrue(hasViolation(result, "INVALID_SHIPPING"));
    }

    @Test
    void requestsModificationForPreferenceMismatchWithoutBlocking() {
        IntentContract intent = intent(new BigDecimal("1000"), new BigDecimal("0.94"), Map.of(), Map.of("color", "black"));
        ValidationResult result = validator.validate(intent,
                proposal(item("Developer Laptop", "899", false, false, false, Map.of("color", "silver"))));

        assertEquals(ValidationDecision.MODIFY, result.decision());
        assertTrue(result.violations().isEmpty());
        assertEquals(1, result.recommendations().size());
    }

    @Test
    void blocksQuantityAbovePerItemLimit() {
        IntentContract intent = intent(new BigDecimal("1000"), new BigDecimal("0.94"), Map.of(), Map.of(), Map.of("Developer Laptop", 1));
        Item item = new Item("Developer Laptop", "laptop", 2, new BigDecimal("400"), false, false, false,
                "Example Store", Map.of());

        ValidationResult result = validator.validate(intent, proposal(item));

        assertEquals(ValidationDecision.BLOCK, result.decision());
        assertTrue(hasViolation(result, "QUANTITY_EXCEEDED"));
    }

    private static IntentContract intent(BigDecimal maximum, BigDecimal confidence, Map<String, String> required, Map<String, String> preferred) {
        return intent(maximum, confidence, required, preferred, Map.of());
    }

    private static IntentContract intent(BigDecimal maximum, BigDecimal confidence, Map<String, String> required,
                                         Map<String, String> preferred, Map<String, Integer> quantityLimits) {
        return new IntentContract("INT-1", "USER-1", Instant.now(), "Buy a development laptop", "Development laptop",
                maximum, "USD", Set.of("laptop"), required, preferred, Set.of(), false, false, false,
                maximum, Set.of(), quantityLimits, null, confidence, "BUYER_APPROVAL");
    }

    private static TransactionProposal proposal(Item... items) {
        BigDecimal itemsTotal = List.of(items).stream()
                .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tax = new BigDecimal("72");
        return new TransactionProposal("PROP-1", "INT-1", "USD", List.of(items), BigDecimal.ZERO, tax, itemsTotal.add(tax));
    }

    private static Item item(String name, String price, boolean recurring, boolean subscription, boolean addOn, Map<String, String> attributes) {
        return new Item(name, "laptop", 1, new BigDecimal(price), recurring, subscription, addOn, "Example Store", attributes);
    }

    private static boolean hasViolation(ValidationResult result, String type) {
        return result.violations().stream().anyMatch(violation -> violation.type().equals(type));
    }
}