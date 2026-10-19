package com.intentos.merchant;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record MerchantQuote(String quoteId, String intentId, String source, boolean simulation,
        String merchantId, String catalogVersion, String catalogFingerprint, String currency,
        Status status, MerchantQuoteRequest request, List<QuotedLine> lines,
        BigDecimal subtotal, BigDecimal tax, BigDecimal shipping, BigDecimal total,
        Instant createdAt, Instant expiresAt, List<String> missingEvidence, String disclosure) {
    public MerchantQuote {
        lines = List.copyOf(lines);
        missingEvidence = List.copyOf(missingEvidence);
    }

    public enum Status { READY, HOLD }

    public record QuotedLine(MerchantCatalog.Product product, int quantity, BigDecimal lineTotal) {}
}