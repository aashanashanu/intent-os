package com.intentos.intent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record OfferEvidence(
        String source,
        String productId,
        String productName,
        String category,
        String retailer,
        String offerUrl,
        BigDecimal price,
        String currency,
        String availability,
                Map<String, String> attributes,
                Instant retrievedAt) {

        public OfferEvidence(String source, String productId, String productName, String retailer,
                        String offerUrl, BigDecimal price, String currency, String availability, Instant retrievedAt) {
                this(source, productId, productName, "", retailer, offerUrl, price, currency,
                                availability, Map.of(), retrievedAt);
        }

        public OfferEvidence {
                attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        }
}