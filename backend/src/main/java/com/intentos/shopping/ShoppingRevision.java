package com.intentos.shopping;

import java.time.Instant;
import java.util.List;

public record ShoppingRevision(
        String revisionId,
        String proposalId,
        String runId,
        String intentId,
        int revisionNumber,
        String parentRevisionId,
        Instant createdAt,
        String quoteId,
        List<CartItem> items,
        List<String> candidateIds,
        String selectionRationale,
        String modelId,
        String modelTask,
        String validationDecision,
        List<String> violationTypes,
        String repairAction,
        int repairAttempts,
        String finalReviewId) {

    public ShoppingRevision {
        items = items == null ? List.of() : List.copyOf(items);
        candidateIds = candidateIds == null ? List.of() : List.copyOf(candidateIds);
        violationTypes = violationTypes == null ? List.of() : List.copyOf(violationTypes);
    }

    public record CartItem(String itemId, String productId, String variantId, int quantity) {}
}