package com.intentos.shopping;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "intentos.elastic.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryShoppingRunStore implements ShoppingRunStore {
    private final ConcurrentMap<String, ShoppingRun> runs = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ShoppingRevision> revisions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ShoppingReview> reviews = new ConcurrentHashMap<>();

    @Override
    public ShoppingRun createRun(ShoppingRun run) {
        if (runs.putIfAbsent(run.runId(), run) != null) {
            throw new IllegalStateException("Shopping run already exists");
        }
        return run;
    }

    @Override
    public Optional<ShoppingRun> findRun(String runId) {
        return Optional.ofNullable(runs.get(runId));
    }

    @Override
    public List<ShoppingRun> findRunsByIntentId(String intentId) {
        return runs.values().stream().filter(run -> run.intentId().equals(intentId))
                .sorted(Comparator.comparing(ShoppingRun::createdAt).reversed()).toList();
    }

    @Override
    public ShoppingRevision createRevision(ShoppingRevision revision) {
        if (revisions.putIfAbsent(revision.revisionId(), revision) != null) {
            throw new IllegalStateException("Shopping revision already exists");
        }
        return revision;
    }

    @Override
    public List<ShoppingRevision> findRevisions(String runId) {
        return revisions.values().stream().filter(revision -> revision.runId().equals(runId))
                .sorted(Comparator.comparingInt(ShoppingRevision::revisionNumber)).toList();
    }

    @Override
    public ShoppingReview saveReview(ShoppingReview review) {
        reviews.putIfAbsent(review.reviewId(), review);
        return reviews.get(review.reviewId());
    }

    @Override
    public Optional<ShoppingReview> findReviewForRevision(String runId, String revisionId, String userId) {
        return reviews.values().stream().filter(review -> review.runId().equals(runId)
                && review.revisionId().equals(revisionId) && review.userId().equals(userId)).findFirst();
    }
}