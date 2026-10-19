package com.intentos.shopping;

import com.intentos.intent.IntentContract;
import com.intentos.intent.IntentDecisionEvent;
import com.intentos.intent.IntentDecisionEventStore;
import com.intentos.intent.IntentLanguageModel;
import com.intentos.intent.IntentModelTask;
import com.intentos.intent.ShoppingCandidate;
import com.intentos.intent.ShoppingSelection;
import com.intentos.intent.TransactionProposal;
import com.intentos.intent.TransactionProposal.Item;
import com.intentos.merchant.MerchantQuote;
import com.intentos.merchant.MerchantQuoteRequest;
import com.intentos.merchant.MerchantQuoteRequest.Line;
import com.intentos.merchant.MerchantCatalog;
import com.intentos.merchant.SimulatedMerchantService;
import com.intentos.repair.IntentRepairService;
import com.intentos.repair.RepairResult;
import com.intentos.validation.IntentPolicyValidator;
import com.intentos.validation.ValidationDecision;
import com.intentos.validation.ValidationResult;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class ShoppingRunService {
    private final ShoppingRunStore store;
    private final SimulatedMerchantService merchant;
    private final IntentLanguageModel languageModel;
    private final IntentPolicyValidator validator;
    private final IntentDecisionEventStore events;
    private final IntentRepairService repairService;
    private final Clock clock;

    @Autowired
    public ShoppingRunService(ShoppingRunStore store, SimulatedMerchantService merchant,
            IntentLanguageModel languageModel, IntentPolicyValidator validator, IntentDecisionEventStore events,
            IntentRepairService repairService) {
        this(store, merchant, languageModel, validator, events, repairService, Clock.systemUTC());
    }

    ShoppingRunService(ShoppingRunStore store, SimulatedMerchantService merchant,
            IntentLanguageModel languageModel, IntentPolicyValidator validator, IntentDecisionEventStore events,
            IntentRepairService repairService, Clock clock) {
        this.store = store;
        this.merchant = merchant;
        this.languageModel = languageModel;
        this.validator = validator;
        this.events = events;
        this.repairService = repairService;
        this.clock = clock;
    }

    public ShoppingRun createRun(IntentContract intent) {
        return store.createRun(new ShoppingRun("RUN-" + UUID.randomUUID(), intent.intentId(), clock.instant()));
    }

    public ShoppingRevision appendRevision(String intentId, String runId, RevisionRequest request) {
        ShoppingRun run = store.findRun(runId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shopping run not found"));
        if (!run.intentId().equals(intentId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shopping run not found");
        }
        if (request == null || request.quoteId() == null || request.items() == null || request.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A quote-backed cart revision is required");
        }
        return appendRevision(intentId, runId, request, null, null, 0);
    }

    private ShoppingRevision appendRevision(String intentId, String runId, RevisionRequest request,
            ValidationResult validation, String repairAction, int repairAttempts) {
        ShoppingRun run = store.findRun(runId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shopping run not found"));
        if (!run.intentId().equals(intentId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shopping run not found");
        }
        if (request == null || request.quoteId() == null || request.items() == null || request.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A quote-backed cart revision is required");
        }
        MerchantQuote quote = merchant.verifyQuoteEvidence(run.intentId(), request.quoteId());
        Map<String, Integer> quotedLines = new HashMap<>();
        quote.request().lines().forEach(line -> quotedLines.put(line.productId() + ":" + line.variantId(), line.quantity()));
        Map<String, Integer> revisionLines = new HashMap<>();
        List<ShoppingRevision.CartItem> items = new ArrayList<>();
        for (RevisionItem item : request.items()) {
            if (item == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart revision contains an invalid item");
            }
            String key = item.productId() + ":" + item.variantId();
            if (item.productId() == null || item.variantId() == null || item.quantity() < 1
                    || revisionLines.putIfAbsent(key, item.quantity()) != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart revision contains an invalid or duplicate item");
            }
            items.add(new ShoppingRevision.CartItem("ITEM-" + UUID.randomUUID(), item.productId(), item.variantId(), item.quantity()));
        }
        if (!quotedLines.equals(revisionLines)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "MERCHANT_QUOTE_CART_MISMATCH");
        }
        List<ShoppingRevision> previous = store.findRevisions(runId);
        int revisionNumber = previous.size() + 1;
        String revisionId = runId + "-R" + revisionNumber;
        String parentRevisionId = previous.isEmpty() ? null : previous.get(previous.size() - 1).revisionId();
        ShoppingRevision revision = new ShoppingRevision(revisionId, "PROP-" + revisionId, runId, run.intentId(), revisionNumber,
                parentRevisionId, clock.instant(), quote.quoteId(), items,
                request.candidateIds(), request.selectionRationale(), request.modelId(), request.modelTask(),
                validation == null ? null : validation.decision().name(),
                validation == null ? List.of() : validation.violations().stream().map(v -> v.type()).toList(),
                repairAction, repairAttempts, null);
        return store.createRevision(revision);
    }

            public ProposalResult propose(IntentContract intent, String runId, DestinationRequest destination) {
            ShoppingRun run = store.findRun(runId)
                .filter(existing -> existing.intentId().equals(intent.intentId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shopping run not found"));
            if (!store.findRevisions(runId).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "SHOPPING_PROPOSAL_ALREADY_CREATED_START_A_NEW_RUN");
            }
            if (destination == null || blank(destination.country()) || blank(destination.region())
                || blank(destination.shippingMethod())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Country, region, and shipping method are required");
            }
            var catalog = merchant.search("");
            if (catalog.products().isEmpty()) {
                return new ProposalResult(run, null, null, null, null, "No merchant candidates are available.");
            }
            List<ShoppingCandidate> candidates = catalog.products().stream().limit(50)
                .map(product -> candidate(product)).toList();
            ShoppingSelection selection = languageModel.selectShopping(intent, candidates)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Autonomous shopping is unavailable; no proposal was created"));
            if (selection.items().isEmpty()) {
                return new ProposalResult(run, null, null, null, null,
                    "No eligible merchant candidate was selected: " + selection.rationale());
            }
            java.util.Map<String, ShoppingCandidate> byId = candidates.stream()
                .collect(java.util.stream.Collectors.toMap(ShoppingCandidate::candidateId, candidate -> candidate));
            List<Line> lines = new ArrayList<>();
            List<Item> proposalItems = new ArrayList<>();
            List<ShoppingRunService.RevisionItem> revisionItems = new ArrayList<>();
            for (ShoppingSelection.SelectedCandidate selected : selection.items()) {
                ShoppingCandidate candidate = byId.get(selected.candidateId());
                if (candidate == null || selected.quantity() > 50) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Shopping selection referenced an unknown candidate");
                }
                lines.add(new Line(candidate.productId(), candidate.variantId(), selected.quantity()));
                revisionItems.add(new RevisionItem(candidate.productId(), candidate.variantId(), selected.quantity()));
            }
            MerchantQuote quote = merchant.createQuote(intent.intentId(), new MerchantQuoteRequest(
                lines, destination.country(), destination.region(), destination.shippingMethod()));
            for (var quotedLine : quote.lines()) {
                MerchantCatalog.Product product = quotedLine.product();
                boolean subscription = product.purchaseType() == MerchantCatalog.PurchaseType.SUBSCRIPTION;
                proposalItems.add(new Item(product.name(), product.category(), quotedLine.quantity(), product.price(),
                    subscription, subscription, false, catalog.merchantId(), product.attributes()));
            }
            TransactionProposal proposal = new TransactionProposal("PROP-" + UUID.randomUUID(), intent.intentId(),
                quote.currency(), proposalItems, quote.shipping(), quote.tax(), quote.total());
            ValidationResult policyResult = validator.validate(intent, proposal);
            if (quote.status() == MerchantQuote.Status.HOLD && policyResult.decision() != ValidationDecision.HOLD) {
                policyResult = new ValidationResult(ValidationDecision.HOLD, policyResult.calculatedTotal(),
                    policyResult.violations(), policyResult.recommendations(), "Merchant quote evidence is incomplete.");
            }
            RevisionRequest revisionRequest = new RevisionRequest(quote.quoteId(), revisionItems,
                selection.items().stream().map(ShoppingSelection.SelectedCandidate::candidateId).toList(),
                selection.rationale(), selection.modelId(), IntentModelTask.SHOPPING.name());
            ShoppingRevision revision = appendRevision(intent.intentId(), runId, revisionRequest, policyResult, null, 0);
            proposal = new TransactionProposal(revision.proposalId(), proposal.intentId(), proposal.currency(),
                proposal.items(), proposal.shipping(), proposal.tax(), proposal.total());
            events.append(new IntentDecisionEvent("EVT-" + UUID.randomUUID(), intent.intentId(),
                revision.proposalId(), clock.instant(), "SHOPPING_VALIDATION", policyResult.decision(),
                policyResult.calculatedTotal(), policyResult.violations(), policyResult.explanation(),
                proposal, runId, revision.revisionId()));
            String outcome = quote.status() == MerchantQuote.Status.HOLD ? "Quote evidence is incomplete; user review is required."
                : policyResult.decision() == ValidationDecision.APPROVE ? "Proposal passed deterministic policy validation; final cart review is still required."
                : "Proposal did not pass deterministic policy validation; repair or user review is required.";
            return new ProposalResult(run, revision, quote, proposal, policyResult, outcome);
            }

            private static ShoppingCandidate candidate(MerchantCatalog.Product product) {
            return new ShoppingCandidate(product.productId() + ":" + product.variantId(), product.productId(),
                product.variantId(), product.name(), product.category(), product.attributes(), product.price(),
                product.stock(), product.maximumQuantity(), product.purchaseType() == null ? null : product.purchaseType().name(),
                product.billingInterval(), product.taxable());
            }

            private static boolean blank(String value) { return value == null || value.isBlank(); }

    public List<ShoppingRun> findRuns(IntentContract intent) {
        return store.findRunsByIntentId(intent.intentId());
    }

    public List<ShoppingRevision> findRevisions(String runId) {
        if (store.findRun(runId).isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shopping run not found");
        return store.findRevisions(runId);
    }

    public ShoppingReview reviewRevision(String intentId, String runId, String revisionId, String userId) {
        ShoppingRevision revision = findRevisions(runId).stream()
                .filter(item -> item.revisionId().equals(revisionId) && item.intentId().equals(intentId))
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shopping revision not found"));
        if (!"APPROVE".equals(revision.validationDecision())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a deterministically approved revision can be reviewed for checkout");
        }
        MerchantQuote quote = merchant.verifyQuote(intentId, revision.quoteId());
        String reviewId = "REVIEW-" + UUID.nameUUIDFromBytes((runId + ":" + revisionId + ":" + userId)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return store.saveReview(new ShoppingReview(reviewId, intentId, runId, revisionId,
                quote.quoteId(), userId, clock.instant()));
    }

    public ShoppingReview findReview(String runId, String revisionId, String userId) {
        return store.findReviewForRevision(runId, revisionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Final review is required for this revision"));
    }

        public RepairProposalResult repairRevision(IntentContract intent, String runId, String revisionId) {
        ShoppingRevision source = findRevisions(runId).stream()
            .filter(item -> item.revisionId().equals(revisionId) && item.intentId().equals(intent.intentId()))
            .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shopping revision not found"));
        List<ShoppingRevision> revisions = findRevisions(runId);
        if (revisions.isEmpty() || !revisions.getLast().revisionId().equals(revisionId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only the latest cart revision can be repaired");
        }
        if ("APPROVE".equals(source.validationDecision())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An approved revision does not require repair");
        }
        int usedAttempts = revisions.stream().mapToInt(ShoppingRevision::repairAttempts).sum();
        int remainingAttempts = IntentRepairService.MAX_ATTEMPTS - usedAttempts;
        if (remainingAttempts < 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "REPAIR_ATTEMPT_LIMIT_REACHED");
        }
        MerchantQuote sourceQuote = merchant.verifyQuoteEvidence(intent.intentId(), source.quoteId());
        TransactionProposal original = ShoppingCheckoutController.proposal(source, sourceQuote);
        RepairResult repaired = repairService.repair(intent, original, remainingAttempts);
        if (repaired.attempts() == 0 || repaired.proposal().items().isEmpty()) {
            events.append(new IntentDecisionEvent("EVT-" + UUID.randomUUID(), intent.intentId(), source.proposalId(),
                clock.instant(), "REPAIR_NEEDS_USER_REVIEW", source.validationDecision() == null
                    ? ValidationDecision.HOLD : ValidationDecision.valueOf(source.validationDecision()),
                null, List.of(), repaired.explanation(), original, runId, source.revisionId()));
            return new RepairProposalResult(repaired, null, null, null, null,
                "No safe repair is available; user review is required.");
        }
        List<Line> repairedLines = new ArrayList<>();
        for (Item repairedItem : repaired.proposal().items()) {
            var match = sourceQuote.lines().stream().map(line -> line.product())
                .filter(product -> product.name().equalsIgnoreCase(repairedItem.name())
                    && java.util.Objects.equals(product.category(), repairedItem.category())
                    && product.price().compareTo(repairedItem.unitPrice()) == 0
                    && product.attributes().equals(repairedItem.attributes()))
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                    "A repaired item lacks matching simulator product evidence"));
            repairedLines.add(new Line(match.productId(), match.variantId(), repairedItem.quantity()));
        }
        MerchantQuote quote = merchant.createQuote(intent.intentId(), new MerchantQuoteRequest(repairedLines,
            sourceQuote.request().country(), sourceQuote.request().region(), sourceQuote.request().shippingMethod()));
        List<RevisionItem> revisionItems = repairedLines.stream()
            .map(line -> new RevisionItem(line.productId(), line.variantId(), line.quantity())).toList();
        RevisionRequest request = new RevisionRequest(quote.quoteId(), revisionItems, List.of(),
            "Deterministic repair from revision " + source.revisionId(), null, IntentModelTask.REPAIR.name());
        TransactionProposal candidate = proposalFromQuote("pending", intent.intentId(), quote);
        ValidationResult result = validator.validate(intent, candidate);
        if (quote.status() == MerchantQuote.Status.HOLD && result.decision() != ValidationDecision.HOLD) {
            result = new ValidationResult(ValidationDecision.HOLD, result.calculatedTotal(), result.violations(),
                result.recommendations(), "Merchant quote evidence is incomplete.");
        }
        ShoppingRevision revision = appendRevision(intent.intentId(), runId, request, result,
            repaired.explanation(), repaired.attempts());
        TransactionProposal finalProposal = proposalFromQuote(revision.proposalId(), intent.intentId(), quote);
        for (var attempt : repaired.history()) {
            events.append(new IntentDecisionEvent("EVT-" + UUID.randomUUID(), intent.intentId(), revision.proposalId(),
                clock.instant(), "REPAIR_ATTEMPT", attempt.validation().decision(),
                attempt.validation().calculatedTotal(), attempt.validation().violations(),
                "Repair attempt " + (usedAttempts + attempt.attempt()) + ": " + attempt.action(),
                attempt.proposal(), runId, revision.revisionId()));
        }
        events.append(new IntentDecisionEvent("EVT-" + UUID.randomUUID(), intent.intentId(), revision.proposalId(),
            clock.instant(), "REPAIR_COMPLETED", result.decision(), result.calculatedTotal(), result.violations(),
            repaired.explanation(), finalProposal, runId, revision.revisionId()));
        return new RepairProposalResult(repaired, revision, quote, finalProposal, result,
            "A new quote-backed revision was created; review is still required.");
        }

        private static TransactionProposal proposalFromQuote(String proposalId, String intentId, MerchantQuote quote) {
        List<Item> items = quote.lines().stream().map(line -> {
            var product = line.product();
            boolean subscription = product.purchaseType() == MerchantCatalog.PurchaseType.SUBSCRIPTION;
            return new Item(product.name(), product.category(), line.quantity(), product.price(), subscription,
                subscription, false, quote.merchantId(), product.attributes());
        }).toList();
        return new TransactionProposal(proposalId, intentId, quote.currency(), items,
            quote.shipping(), quote.tax(), quote.total());
        }

    public record RevisionRequest(String quoteId, List<RevisionItem> items, List<String> candidateIds,
            String selectionRationale, String modelId, String modelTask) {}

    public record RevisionItem(String productId, String variantId, int quantity) {}

    public record DestinationRequest(String country, String region, String shippingMethod) {}

    public record ProposalResult(ShoppingRun run, ShoppingRevision revision, MerchantQuote quote,
            TransactionProposal proposal, ValidationResult validation, String outcome) {}

        public record RepairProposalResult(RepairResult repair, ShoppingRevision revision, MerchantQuote quote,
            TransactionProposal proposal, ValidationResult validation, String outcome) {}
}