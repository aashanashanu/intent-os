package com.intentos.merchant;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record MerchantCatalog(String merchantId, String version, String currency,
        List<Product> products, List<DestinationRule> destinationRules) {
    public MerchantCatalog {
        products = List.copyOf(products);
        destinationRules = List.copyOf(destinationRules);
    }

    public record Product(String productId, String variantId, String name, String category,
            Map<String, String> attributes, BigDecimal price, Integer stock, Integer maximumQuantity,
            PurchaseType purchaseType, String billingInterval, Boolean taxable) {
        public Product {
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        }
    }

    public enum PurchaseType { ONE_TIME, SUBSCRIPTION }

    public record DestinationRule(String country, String region, String shippingMethod,
            BigDecimal shipping, BigDecimal taxRate, Boolean shippingTaxable) {}
}