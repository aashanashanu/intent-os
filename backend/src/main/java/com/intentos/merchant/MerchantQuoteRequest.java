package com.intentos.merchant;

import java.util.List;

public record MerchantQuoteRequest(List<Line> lines, String country, String region, String shippingMethod) {
    public MerchantQuoteRequest {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    public record Line(String productId, String variantId, int quantity) {}
}