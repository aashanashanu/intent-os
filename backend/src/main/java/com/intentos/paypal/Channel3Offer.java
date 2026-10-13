package com.intentos.paypal;

import com.intentos.intent.OfferEvidence;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record Channel3Offer(
        String productId,
        String productName,
        String description,
        String category,
        Map<String, String> attributes,
        String retailer,
        String offerUrl,
        BigDecimal price,
        String currency,
        String availability,
        String condition,
        Instant retrievedAt) {

    public OfferEvidence evidence() {
        return new OfferEvidence("channel3", productId, productName, category, retailer, offerUrl,
            price, currency, availability, attributes, retrievedAt);
    }
}