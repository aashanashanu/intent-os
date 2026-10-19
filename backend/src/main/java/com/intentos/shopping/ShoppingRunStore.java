package com.intentos.shopping;

import java.util.List;
import java.util.Optional;

public interface ShoppingRunStore {
    ShoppingRun createRun(ShoppingRun run);

    Optional<ShoppingRun> findRun(String runId);

    List<ShoppingRun> findRunsByIntentId(String intentId);

    ShoppingRevision createRevision(ShoppingRevision revision);

    List<ShoppingRevision> findRevisions(String runId);

    ShoppingReview saveReview(ShoppingReview review);

    Optional<ShoppingReview> findReviewForRevision(String runId, String revisionId, String userId);
}