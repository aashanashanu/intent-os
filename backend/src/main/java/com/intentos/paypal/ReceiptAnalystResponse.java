package com.intentos.paypal;

import java.util.List;

public record ReceiptAnalystResponse(String answer, List<String> receiptIds, int evidenceCount,
    List<String> eventIds, List<String> runIds, List<String> revisionIds,
    String retrievalMode, String narrationMode) {
    public ReceiptAnalystResponse(String answer, List<String> receiptIds, int evidenceCount) {
    this(answer, receiptIds, evidenceCount, List.of(), List.of(), List.of(), "LEXICAL", "LEGACY");
    }

    public ReceiptAnalystResponse {
        receiptIds = List.copyOf(receiptIds);
        eventIds = List.copyOf(eventIds);
        runIds = List.copyOf(runIds);
        revisionIds = List.copyOf(revisionIds);
    }
}