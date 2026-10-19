package com.intentos.intent;

import java.math.BigDecimal;
import java.util.Map;

public record ShoppingCandidate(String candidateId, String productId, String variantId, String name,
        String category, Map<String, String> attributes, BigDecimal price, Integer stock,
        Integer maximumQuantity, String purchaseType, String billingInterval, Boolean taxable) {}