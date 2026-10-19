package com.intentos.shopping;

import com.intentos.intent.IntentAccessService;
import com.intentos.intent.IntentDecisionEvent;
import com.intentos.intent.IntentDecisionEventStore;
import com.intentos.intent.TransactionProposal;
import com.intentos.intent.TransactionProposal.Item;
import com.intentos.merchant.MerchantQuote;
import com.intentos.merchant.SimulatedMerchantService;
import com.intentos.paypal.PayPalCheckoutSession;
import com.intentos.paypal.PayPalCheckoutStore;
import com.intentos.paypal.PayPalOrderResponse;
import com.intentos.paypal.PayPalOrdersService;
import com.intentos.validation.IntentPolicyValidator;
import com.intentos.validation.ValidationDecision;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/intents/{intentId}/shopping-runs/{runId}/revisions/{revisionId}/orders")
public class ShoppingCheckoutController {
    private final IntentAccessService access;
    private final ShoppingRunService runs;
    private final SimulatedMerchantService merchant;
    private final IntentPolicyValidator validator;
    private final PayPalOrdersService orders;
    private final PayPalCheckoutStore checkouts;
    private final IntentDecisionEventStore events;

    public ShoppingCheckoutController(IntentAccessService access, ShoppingRunService runs,
            SimulatedMerchantService merchant, IntentPolicyValidator validator, PayPalOrdersService orders,
            PayPalCheckoutStore checkouts, IntentDecisionEventStore events) {
        this.access = access;
        this.runs = runs;
        this.merchant = merchant;
        this.validator = validator;
        this.orders = orders;
        this.checkouts = checkouts;
        this.events = events;
    }

    @PostMapping
    public PayPalOrderResponse create(@PathVariable String intentId, @PathVariable String runId,
            @PathVariable String revisionId, HttpServletRequest request) {
        var intent = access.findAccessible(intentId, request)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Intent not found"));
        ShoppingRevision revision = runs.findRevisions(runId).stream()
                .filter(item -> item.revisionId().equals(revisionId) && item.intentId().equals(intentId))
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shopping revision not found"));
        if (!"APPROVE".equals(revision.validationDecision())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only an approved revision can create an order");
        }
        Object principal = request.getAttribute("paypalUserId");
        String userId = principal instanceof String value ? value : intent.userId();
        ShoppingReview review = runs.findReview(runId, revisionId, userId);
        MerchantQuote quote = merchant.verifyQuote(intentId, revision.quoteId());
        if (!review.quoteId().equals(quote.quoteId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Final review does not match the current quote");
        }
        TransactionProposal proposal = proposal(revision, quote);
        var validation = validator.validate(intent, proposal);
        if (validation.decision() != ValidationDecision.APPROVE || validation.calculatedTotal().compareTo(quote.total()) != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Current intent policy does not approve this revision");
        }
        PayPalOrderResponse order = orders.createOrder(intentId, proposal, quote.total(), quote.currency());
        checkouts.save(new PayPalCheckoutSession(order.orderId(), intentId, proposal.proposalId(), proposal,
                quote.total(), quote.currency(), PayPalCheckoutSession.State.CREATED, null, runId, revisionId));
        appendEvent(intentId, proposal, runId, revisionId, "PAYPAL_ORDER_CREATED", quote.total(),
                "PayPal Sandbox order created from reviewed simulated-merchant quote " + quote.quoteId());
        return order;
    }

    public static TransactionProposal proposal(ShoppingRevision revision, MerchantQuote quote) {
        List<Item> items = quote.lines().stream().map(line -> {
            var product = line.product();
            boolean subscription = product.purchaseType() == com.intentos.merchant.MerchantCatalog.PurchaseType.SUBSCRIPTION;
            return new Item(product.name(), product.category(), line.quantity(), product.price(),
                    subscription, subscription, false, quote.merchantId(), product.attributes());
        }).toList();
        return new TransactionProposal(revision.proposalId(), revision.intentId(), quote.currency(), items,
                quote.shipping(), quote.tax(), quote.total());
    }

    private void appendEvent(String intentId, TransactionProposal proposal, String runId,
            String revisionId, String eventType, java.math.BigDecimal amount, String message) {
        events.append(new IntentDecisionEvent("EVT-" + UUID.randomUUID(), intentId, proposal.proposalId(),
                Instant.now(), eventType, ValidationDecision.APPROVE, amount, List.of(), message, proposal,
                runId, revisionId));
    }
}