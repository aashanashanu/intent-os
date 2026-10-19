package com.intentos.merchant;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MerchantQuoteCalculatorTest {
    private final MerchantQuoteCalculator calculator = new MerchantQuoteCalculator();
    private final Instant now = Instant.parse("2026-10-19T10:00:00Z");

    @Test
    void computesCostsFromMerchantRulesWithoutSelectingProducts() {
        MerchantQuote quote = quote(catalog(MerchantCatalog.PurchaseType.ONE_TIME), "CA", 2);
        assertEquals(MerchantQuote.Status.READY, quote.status());
        assertEquals(0, new BigDecimal("226.00").compareTo(quote.total()));
        assertEquals(0, new BigDecimal("16.00").compareTo(quote.tax()));
        assertTrue(quote.simulation());
        assertEquals("simulated-merchant", quote.source());
    }

    @Test
    void unknownTermsCannotBecomeOneTimePurchase() {
        MerchantQuote quote = quote(catalog(null), "CA", 1);
        assertEquals(MerchantQuote.Status.HOLD, quote.status());
        assertTrue(quote.missingEvidence().contains("UNKNOWN_PURCHASE_TERMS:product:variant"));
        assertNull(quote.total());
    }

    @Test
    void unsupportedDestinationDoesNotBecomeFreeShippingOrZeroTax() {
        MerchantQuote quote = quote(catalog(MerchantCatalog.PurchaseType.ONE_TIME), "UNKNOWN", 1);
        assertEquals(MerchantQuote.Status.HOLD, quote.status());
        assertNull(quote.shipping());
        assertNull(quote.tax());
        assertNull(quote.total());
    }

    @Test
    void stockCannotBeExceeded() {
        MerchantQuote quote = quote(catalog(MerchantCatalog.PurchaseType.ONE_TIME), "CA", 4);
        assertEquals(MerchantQuote.Status.HOLD, quote.status());
        assertTrue(quote.missingEvidence().contains("INSUFFICIENT_STOCK:product:variant"));
    }

    private MerchantQuote quote(MerchantCatalog catalog, String region, int quantity) {
        return calculator.calculate("QUOTE-1", "INT-1", catalog, "fingerprint",
                new MerchantQuoteRequest(List.of(new MerchantQuoteRequest.Line("product", "variant", quantity)),
                        "US", region, "standard"), now, now.plusSeconds(300));
    }

    private MerchantCatalog catalog(MerchantCatalog.PurchaseType purchaseType) {
        return new MerchantCatalog("test-store", "1", "USD", List.of(new MerchantCatalog.Product(
                "product", "variant", "Test Product", "laptop", Map.of("ram", "16GB"),
                new BigDecimal("100.00"), 3, 5, purchaseType, null, true)),
                List.of(new MerchantCatalog.DestinationRule("US", "CA", "standard",
                        new BigDecimal("10.00"), new BigDecimal("0.08"), false)));
    }
}