package com.intentos.merchant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intentos.intent.IntentContract;
import com.intentos.intent.TransactionProposal;
import com.intentos.repair.IntentRepairService;
import com.intentos.repair.RepairStatus;
import com.intentos.validation.IntentPolicyValidator;
import com.intentos.validation.ValidationDecision;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeedCatalogScenarioTest {
    private MerchantCatalog catalog;
    private final IntentPolicyValidator validator = new IntentPolicyValidator();
    private final IntentRepairService repairs = new IntentRepairService(validator);

    @BeforeEach
    void loadSeedCatalog() throws Exception {
        try (var input = getClass().getResourceAsStream("/merchant/catalog.json")) {
            catalog = new ObjectMapper().readValue(input, MerchantCatalog.class);
        }
    }

    @Test
    void candidatesHaveUniqueStableIdentitiesAndFitTheShoppingLimit() {
        assertEquals("2", catalog.version());
        assertTrue(catalog.products().size() > 20);
        assertTrue(catalog.products().size() <= 50);
        assertEquals(catalog.products().size(), catalog.products().stream()
                .map(product -> product.productId() + ":" + product.variantId()).distinct().count());
    }

    @ParameterizedTest
    @CsvSource({
            "portable-computer,16gb-512gb,1,854.40",
            "workstation-computer,32gb-1tb,1,1221.60",
            "compact-computer,8gb-256gb,1,508.80",
            "travel-tablet,8gb-128gb,1,357.60",
            "studio-monitor,27inch-4k,1,292.80",
            "desk-monitor,24inch-1080p,1,163.20",
            "mechanical-keyboard,wireless,1,93.00",
            "usb-keyboard,wired,2,66.00",
            "desk-chair,ergonomic,1,206.40",
            "desk-chair,premium,1,573.60",
            "notebook-pack,5pack,3,60.60",
            "party-kit,12guests,1,60.60"
    })
    void completeListingsHaveDocumentedCaliforniaTotals(String productId, String variantId,
            int quantity, String expected) {
        MerchantQuote quote = quote(new MerchantQuoteRequest.Line(productId, variantId, quantity));
        assertEquals(MerchantQuote.Status.READY, quote.status());
        assertEquals(0, new BigDecimal(expected).compareTo(quote.total()));
    }

    @ParameterizedTest
    @CsvSource({
            "sold-out-camera,4k,1,INSUFFICIENT_STOCK",
            "limited-headphones,noise-cancelling,2,INSUFFICIENT_STOCK",
            "mechanical-keyboard,wireless,6,QUANTITY_LIMIT",
            "tax-pending-printer,wireless,1,UNKNOWN_TAX_TREATMENT",
            "terms-pending-software,license-pending,1,UNKNOWN_PURCHASE_TERMS",
            "interval-pending-plan,billing-pending,1,UNKNOWN_BILLING_INTERVAL",
            "stock-pending-drive,1tb,1,INSUFFICIENT_STOCK",
            "price-pending-projector,1080p,1,INVALID_PRICE"
    })
    void incompleteListingsHoldWithoutInventingFinalCosts(String productId, String variantId,
            int quantity, String reason) {
        MerchantQuote quote = quote(new MerchantQuoteRequest.Line(productId, variantId, quantity));
        assertEquals(MerchantQuote.Status.HOLD, quote.status());
        assertTrue(quote.missingEvidence().contains(reason + ":" + productId + ":" + variantId));
        assertNull(quote.total());
        assertNull(quote.tax());
        assertNull(quote.shipping());
    }

    @Test
    void forbiddenWarrantyAndSubscriptionAreRemovedThenRequoted() {
        IntentContract intent = intent("1000", Set.of("laptop"), Map.of(), Map.of(), Map.of());
        MerchantQuote initial = quote(line("portable-computer", "16gb-512gb"),
                line("device-warranty", "2year"), line("antivirus-plan", "annual"));
        assertEquals(0, new BigDecimal("1154.64").compareTo(initial.total()));
        assertEquals(ValidationDecision.BLOCK, validator.validate(intent, proposal(initial)).decision());

        var result = repairs.repair(intent, proposal(initial));
        assertEquals(RepairStatus.APPROVED, result.status());
        assertEquals(List.of("Portable Computer 16"), result.proposal().items().stream()
                .map(TransactionProposal.Item::name).toList());
        MerchantQuote finalQuote = quote(line("portable-computer", "16gb-512gb"));
        assertEquals(ValidationDecision.APPROVE, validator.validate(intent, proposal(finalQuote)).decision());
        assertEquals(0, new BigDecimal("300.24").compareTo(initial.total().subtract(finalQuote.total())));
    }

    @Test
    void expensiveCoreProductCannotBeSilentlyReplacedAndSpecsCannotBeInvented() {
        IntentContract intent = intent("1000", Set.of("laptop"), Map.of("ram", "32GB"), Map.of(), Map.of());
        for (String candidate : List.of("workstation-computer:32gb-1tb", "compact-computer:8gb-256gb",
                "unconfirmed-laptop:specs-pending")) {
            String[] parts = candidate.split(":");
            TransactionProposal proposal = proposal(quote(line(parts[0], parts[1])));
            assertEquals(ValidationDecision.BLOCK, validator.validate(intent, proposal).decision());
            assertEquals(RepairStatus.NEEDS_USER_REVIEW, repairs.repair(intent, proposal).status());
        }
    }

    @Test
    void quantityRepairRespectsExactProductNameAndRequotesTheReduction() {
        IntentContract intent = intent("100", Set.of("keyboard"), Map.of(), Map.of(),
                Map.of("Everyday USB Keyboard", 1));
        TransactionProposal initial = proposal(quote(new MerchantQuoteRequest.Line("usb-keyboard", "wired", 2)));
        assertEquals(ValidationDecision.BLOCK, validator.validate(intent, initial).decision());
        var result = repairs.repair(intent, initial);
        assertEquals(RepairStatus.APPROVED, result.status());
        assertEquals(1, result.proposal().items().getFirst().quantity());
        assertEquals(0, new BigDecimal("39.00").compareTo(quote(line("usb-keyboard", "wired")).total()));
    }

    @Test
    void preferenceMismatchIsModifyNotPaymentApproval() {
        IntentContract intent = intent("350", Set.of("monitor"), Map.of(),
                Map.of("resolution", "4K"), Map.of());
        var result = validator.validate(intent, proposal(quote(line("desk-monitor", "24inch-1080p"))));
        assertEquals(ValidationDecision.MODIFY, result.decision());
        assertFalse(result.recommendations().isEmpty());
    }

    @Test
    void shippingCanCrossTheBudgetAndUnknownDestinationsHold() {
        IntentContract intent = intent("860", Set.of("laptop"), Map.of(), Map.of(), Map.of());
        List<MerchantQuoteRequest.Line> lines = List.of(line("portable-computer", "16gb-512gb"));
        MerchantQuote express = quote(new MerchantQuoteRequest(lines, "US", "CA", "express"));
        assertEquals(0, new BigDecimal("872.40").compareTo(express.total()));
        assertEquals(ValidationDecision.BLOCK, validator.validate(intent, proposal(express)).decision());
        MerchantQuote unsupported = quote(new MerchantQuoteRequest(lines, "US", "TX", "standard"));
        assertEquals(MerchantQuote.Status.HOLD, unsupported.status());
        assertTrue(unsupported.missingEvidence().contains("DESTINATION_SHIPPING_RULE_UNAVAILABLE"));
    }

    private MerchantQuote quote(MerchantQuoteRequest.Line... lines) {
        return quote(new MerchantQuoteRequest(List.of(lines), "US", "CA", "standard"));
    }

    private MerchantQuote quote(MerchantQuoteRequest request) {
        Instant now = Instant.parse("2026-10-28T12:00:00Z");
        return new MerchantQuoteCalculator().calculate("MQ-TEST", "INT-TEST", catalog,
                "test-fingerprint", request, now, now.plusSeconds(300));
    }

    private static MerchantQuoteRequest.Line line(String productId, String variantId) {
        return new MerchantQuoteRequest.Line(productId, variantId, 1);
    }

    private static TransactionProposal proposal(MerchantQuote quote) {
        List<TransactionProposal.Item> items = quote.lines().stream().map(line -> {
            var product = line.product();
            boolean subscription = product.purchaseType() == MerchantCatalog.PurchaseType.SUBSCRIPTION;
            return new TransactionProposal.Item(product.name(), product.category(), line.quantity(),
                    product.price(), subscription, subscription, false, quote.merchantId(), product.attributes());
        }).toList();
        return new TransactionProposal("PROP-TEST", "INT-TEST", "USD", items,
                quote.shipping(), quote.tax(), quote.total());
    }

    private static IntentContract intent(String budget, Set<String> categories, Map<String, String> required,
            Map<String, String> preferred, Map<String, Integer> quantities) {
        return new IntentContract("INT-TEST", "USER-TEST", Instant.parse("2026-10-28T12:00:00Z"),
                "Scenario test", "Scenario test", new BigDecimal(budget), "USD", categories, required,
                preferred, Set.of(), false, false, false, null, Set.of(), quantities, null, BigDecimal.ONE, null);
    }
}