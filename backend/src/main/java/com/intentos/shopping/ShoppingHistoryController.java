package com.intentos.shopping;

import com.intentos.intent.IntentContract;
import com.intentos.intent.IntentStore;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/shopping-history")
public class ShoppingHistoryController {
    private final IntentStore intents;
    private final ShoppingRunStore runs;

    public ShoppingHistoryController(IntentStore intents, ShoppingRunStore runs) {
        this.intents = intents;
        this.runs = runs;
    }

    @GetMapping
    public HistoryPage search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int pageSize,
            HttpServletRequest request) {
        Object principal = request.getAttribute("paypalUserId");
        if (!(principal instanceof String userId) || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "PAYPAL_LOGIN_REQUIRED");
        }
        if (page < 0 || pageSize < 1 || pageSize > 100 || q.length() > 300 || status.length() > 32
                || (from != null && to != null && from.isAfter(to))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid history filters or pagination");
        }
        List<HistoryEntry> entries = new ArrayList<>();
        boolean incomplete = false;
        for (IntentContract intent : intents.findAllByUserId(userId)) {
            List<ShoppingRun> intentRuns = runs.findRunsByIntentId(intent.intentId());
            if (intentRuns.size() >= 500) incomplete = true;
            for (ShoppingRun run : intentRuns) {
                List<ShoppingRevision> revisions = runs.findRevisions(run.runId());
                if (revisions.size() >= 500) incomplete = true;
                ShoppingRevision latest = revisions.isEmpty() ? null : revisions.getLast();
                Instant occurredAt = latest == null ? run.createdAt() : latest.createdAt();
                if (from != null && occurredAt.isBefore(from) || to != null && occurredAt.isAfter(to)) continue;
                String decision = latest == null || latest.validationDecision() == null ? "IN_PROGRESS" : latest.validationDecision();
                if (!status.isBlank() && !status.equalsIgnoreCase(decision)
                        && revisions.stream().noneMatch(revision -> revision.violationTypes().stream()
                                .anyMatch(type -> type.equalsIgnoreCase(status)))) continue;
                List<String> products = revisions.stream().flatMap(revision -> revision.items().stream())
                        .map(ShoppingRevision.CartItem::productId).distinct().toList();
                String haystack = (intent.goal() + " " + intent.naturalLanguageRequest() + " " + products)
                        .toLowerCase(Locale.ROOT);
                if (!q.isBlank() && !haystack.contains(q.toLowerCase(Locale.ROOT))) continue;
                entries.add(new HistoryEntry(intent.intentId(), intent.goal(), run, revisions, decision, products));
            }
        }
        entries.sort(Comparator.comparing((HistoryEntry entry) -> entry.run().createdAt()).reversed());
        long startLong = (long) page * pageSize;
        int start = (int) Math.min(entries.size(), startLong);
        int end = Math.min(entries.size(), start + pageSize);
        return new HistoryPage(entries.subList(start, end), page, pageSize, entries.size(), incomplete);
    }

    public record HistoryEntry(String intentId, String goal, ShoppingRun run, List<ShoppingRevision> revisions,
            String status, List<String> productIds) {}

    public record HistoryPage(List<HistoryEntry> items, int page, int pageSize, int total, boolean incompleteData) {}
}