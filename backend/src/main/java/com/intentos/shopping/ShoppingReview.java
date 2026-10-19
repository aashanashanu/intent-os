package com.intentos.shopping;

import java.time.Instant;

public record ShoppingReview(String reviewId, String intentId, String runId, String revisionId,
        String quoteId, String userId, Instant reviewedAt) {}