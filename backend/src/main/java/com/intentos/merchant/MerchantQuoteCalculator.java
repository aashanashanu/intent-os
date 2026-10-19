package com.intentos.merchant;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MerchantQuoteCalculator {
    public static final String DISCLOSURE = "Simulated merchant catalog and quotes. "
            + "Real PayPal Sandbox payment lifecycle. No physical order or retailer settlement.";

    public MerchantQuote calculate(String quoteId, String intentId, MerchantCatalog catalog,
            String fingerprint, MerchantQuoteRequest request, Instant createdAt, Instant expiresAt) {
        List<String> missing = new ArrayList<>();
        List<MerchantQuote.QuotedLine> lines = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal taxableSubtotal = BigDecimal.ZERO;
        if (request.lines().isEmpty()) missing.add("EMPTY_CART");
        if (request.lines().size() > 50) missing.add("TOO_MANY_LINES");
        for (MerchantQuoteRequest.Line requested : request.lines().stream().limit(50).toList()) {
            String key = requested.productId() + ":" + requested.variantId();
            if (!seen.add(key)) missing.add("DUPLICATE_LINE:" + key);
            MerchantCatalog.Product product = catalog.products().stream()
                    .filter(candidate -> candidate.productId().equals(requested.productId())
                            && candidate.variantId().equals(requested.variantId()))
                    .findFirst().orElse(null);
            if (product == null) {
                missing.add("UNKNOWN_PRODUCT_VARIANT:" + key);
                continue;
            }
            if (requested.quantity() <= 0) missing.add("INVALID_QUANTITY:" + key);
            if (product.maximumQuantity() == null || product.maximumQuantity() < 1
                    || requested.quantity() > product.maximumQuantity()) missing.add("QUANTITY_LIMIT:" + key);
            if (product.stock() == null || requested.quantity() > product.stock()) missing.add("INSUFFICIENT_STOCK:" + key);
            if (product.purchaseType() == null) missing.add("UNKNOWN_PURCHASE_TERMS:" + key);
            if (product.purchaseType() == MerchantCatalog.PurchaseType.SUBSCRIPTION
                    && (product.billingInterval() == null || product.billingInterval().isBlank())) {
                missing.add("UNKNOWN_BILLING_INTERVAL:" + key);
            }
            if (product.taxable() == null) missing.add("UNKNOWN_TAX_TREATMENT:" + key);
            if (!validMoney(product.price())) {
                missing.add("INVALID_PRICE:" + key);
                continue;
            }
            BigDecimal lineTotal = product.price().multiply(BigDecimal.valueOf(Math.max(0, requested.quantity())));
            subtotal = subtotal.add(lineTotal);
            if (Boolean.TRUE.equals(product.taxable())) taxableSubtotal = taxableSubtotal.add(lineTotal);
            lines.add(new MerchantQuote.QuotedLine(product, requested.quantity(), lineTotal));
        }
        if (catalog.currency() == null || !"USD".equals(catalog.currency())) missing.add("UNSUPPORTED_CURRENCY");
        MerchantCatalog.DestinationRule rule = catalog.destinationRules().stream()
                .filter(candidate -> same(candidate.country(), request.country())
                        && same(candidate.region(), request.region())
                        && same(candidate.shippingMethod(), request.shippingMethod()))
                .findFirst().orElse(null);
        BigDecimal shipping = null;
        BigDecimal tax = null;
        if (rule == null) {
            missing.add("DESTINATION_SHIPPING_RULE_UNAVAILABLE");
        } else {
            if (!validMoney(rule.shipping())) missing.add("UNKNOWN_SHIPPING");
            else shipping = rule.shipping();
            if (rule.taxRate() == null || rule.taxRate().signum() < 0
                    || rule.taxRate().compareTo(BigDecimal.ONE) > 0 || rule.shippingTaxable() == null) {
                missing.add("UNKNOWN_TAX_RULE");
            } else if (shipping != null) {
                BigDecimal taxable = Boolean.TRUE.equals(rule.shippingTaxable())
                        ? taxableSubtotal.add(shipping) : taxableSubtotal;
                tax = taxable.multiply(rule.taxRate()).setScale(2, RoundingMode.HALF_UP);
            }
        }
        boolean ready = missing.isEmpty();
        return new MerchantQuote(quoteId, intentId, "simulated-merchant", true,
                catalog.merchantId(), catalog.version(), fingerprint, catalog.currency(),
                ready ? MerchantQuote.Status.READY : MerchantQuote.Status.HOLD, request, lines,
                subtotal, ready ? tax : null, ready ? shipping : null,
                ready ? subtotal.add(tax).add(shipping) : null,
                createdAt, expiresAt, missing, DISCLOSURE);
    }

    private static boolean same(String configured, String supplied) {
        return configured != null && supplied != null && !supplied.isBlank()
                && configured.equalsIgnoreCase(supplied);
    }

    private static boolean validMoney(BigDecimal value) {
        return value != null && value.signum() >= 0 && value.stripTrailingZeros().scale() <= 2;
    }
}