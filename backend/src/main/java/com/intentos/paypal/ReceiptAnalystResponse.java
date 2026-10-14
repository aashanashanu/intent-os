package com.intentos.paypal;

import java.util.List;

public record ReceiptAnalystResponse(String answer, List<String> receiptIds, int evidenceCount) {
    public ReceiptAnalystResponse {
        receiptIds = List.copyOf(receiptIds);
    }
}