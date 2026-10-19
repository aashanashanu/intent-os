package com.intentos.paypal;

import com.intentos.intent.IntentAccessService;
import com.intentos.intent.IntentDecisionEvent;
import com.intentos.intent.IntentDecisionEventStore;
import com.intentos.intent.TransactionProposal;
import com.intentos.validation.IntentPolicyValidator;
import com.intentos.validation.ValidationDecision;
import com.intentos.shopping.ShoppingCheckoutController;
import com.intentos.shopping.ShoppingRevision;
import com.intentos.shopping.ShoppingRunService;
import com.intentos.merchant.SimulatedMerchantService;
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
@RequestMapping("/api/intents/{intentId}/orders")
public class PayPalCheckoutController {
    private final IntentAccessService intentAccessService;
    private final IntentPolicyValidator validator;
    private final PayPalOrdersService ordersService;
    private final PayPalCheckoutStore checkoutStore;
    private final IntentDecisionEventStore eventStore;
    private final ShoppingRunService shoppingRuns;
    private final SimulatedMerchantService merchant;

    public PayPalCheckoutController(
            IntentAccessService intentAccessService,
            IntentPolicyValidator validator,
            PayPalOrdersService ordersService,
            PayPalCheckoutStore checkoutStore,
            IntentDecisionEventStore eventStore,
            ShoppingRunService shoppingRuns,
            SimulatedMerchantService merchant) {
        this.intentAccessService = intentAccessService;
        this.validator = validator;
        this.ordersService = ordersService;
        this.checkoutStore = checkoutStore;
        this.eventStore = eventStore;
        this.shoppingRuns = shoppingRuns;
        this.merchant = merchant;
    }

    @PostMapping("/{orderId}/capture")
    public PayPalCaptureResponse captureOrder(
            @PathVariable String intentId,
            @PathVariable String orderId,
            HttpServletRequest request) {
        var intent = intentAccessService.findAccessible(intentId, request)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Intent not found"));
        PayPalCheckoutSession session = checkoutStore.find(orderId)
                .filter(checkout -> checkout.intentId().equals(intentId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PayPal order not found for this intent"));
        if (session.capture() != null) return session.capture();
        if (intent.expiration() != null && !intent.expiration().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "INTENT_EXPIRED_BEFORE_CAPTURE");
        }
        TransactionProposal captureProposal = session.proposal();
        if (session.runId() != null || session.revisionId() != null) {
            if (session.runId() == null || session.revisionId() == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "CHECKOUT_LINEAGE_INCOMPLETE");
            }
            ShoppingRevision revision = shoppingRuns.findRevisions(session.runId()).stream()
                    .filter(item -> item.revisionId().equals(session.revisionId())
                            && item.intentId().equals(intentId))
                    .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                            "CHECKOUT_REVISION_UNAVAILABLE"));
            Object principal = request.getAttribute("paypalUserId");
            String userId = principal instanceof String value ? value : intent.userId();
            shoppingRuns.findReview(session.runId(), session.revisionId(), userId);
            var quote = merchant.verifyQuote(intentId, revision.quoteId());
            captureProposal = ShoppingCheckoutController.proposal(revision, quote);
            if (!"APPROVE".equals(revision.validationDecision())
                    || quote.total().compareTo(session.amount()) != 0
                    || !quote.currency().equals(session.currency())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "CHECKOUT_AUTHORIZATION_CHANGED");
            }
            var validation = validator.validate(intent, captureProposal);
            if (validation.decision() != ValidationDecision.APPROVE
                    || validation.calculatedTotal().compareTo(session.amount()) != 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "CURRENT_POLICY_REJECTS_CAPTURE");
            }
        } else {
            var validation = validator.validate(intent, captureProposal);
            if (validation.decision() != ValidationDecision.APPROVE
                    || validation.calculatedTotal().compareTo(session.amount()) != 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "CURRENT_POLICY_REJECTS_CAPTURE");
            }
        }
        if (!checkoutStore.beginCapture(orderId)) {
            PayPalCheckoutSession current = checkoutStore.find(orderId).orElse(session);
            if (current.capture() != null) return current.capture();
            throw new ResponseStatusException(HttpStatus.CONFLICT, "PayPal order capture is already in progress");
        }

        try {
            PayPalCaptureResponse capture = ordersService.captureOrder(session);
            appendEvent(intentId, session.proposalId(), "PAYPAL_CAPTURE_RESULT", ValidationDecision.APPROVE,
                    capture.amount(), List.of(), "PayPal capture " + capture.captureId() + " returned status " + capture.status(),
                    captureProposal, session.runId(), session.revisionId());
            checkoutStore.finishCapture(orderId, capture);
            return capture;
        } catch (RuntimeException exception) {
            checkoutStore.releaseCapture(orderId);
            throw exception;
        }
    }

    private void appendEvent(String intentId, String proposalId, String eventType, ValidationDecision decision,
                java.math.BigDecimal amount, List<com.intentos.validation.PolicyViolation> violations,
                String explanation, TransactionProposal proposalSnapshot, String runId, String revisionId) {
        eventStore.append(new IntentDecisionEvent(
                "EVT-" + UUID.randomUUID(), intentId, proposalId, Instant.now(), eventType,
                decision, amount, violations, explanation, proposalSnapshot, runId, revisionId));
    }

}