package com.intentos.intent;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record TransactionProposal(
        String proposalId,
        String intentId,
        String currency,
        List<Item> items,
        BigDecimal shipping,
        BigDecimal tax,
        BigDecimal total,
        List<OfferEvidence> offerEvidence) {

    public TransactionProposal(String proposalId, String intentId, String currency, List<Item> items,
            BigDecimal shipping, BigDecimal tax, BigDecimal total) {
        this(proposalId, intentId, currency, items, shipping, tax, total, List.of());
    }

    public TransactionProposal {
        items = items == null ? List.of() : List.copyOf(items);
        offerEvidence = offerEvidence == null ? List.of() : List.copyOf(offerEvidence);
    }

    public record Item(
            String name,
            String category,
            int quantity,
            BigDecimal unitPrice,
            boolean recurring,
            boolean subscription,
            boolean addOn,
            String merchant,
            Map<String, String> attributes) {

        public Item {
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        }
    }
}