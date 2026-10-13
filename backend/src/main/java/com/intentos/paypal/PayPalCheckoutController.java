package com.intentos.paypal;

import com.intentos.intent.IntentAccessService;
import com.intentos.intent.IntentDecisionEvent;
import com.intentos.intent.IntentDecisionEventStore;
import com.intentos.intent.OfferEvidence;
import com.intentos.intent.TransactionProposal;
import com.intentos.validation.IntentPolicyValidator;
import com.intentos.validation.ValidationDecision;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/intents/{intentId}/orders")
public class PayPalCheckoutController {
    private final IntentAccessService intentAccessService;
    private final IntentPolicyValidator validator;
    private final PayPalOrdersService ordersService;
    private final Channel3OfferService channel3OfferService;
    private final PayPalCheckoutStore checkoutStore;
    private final IntentDecisionEventStore eventStore;

    public PayPalCheckoutController(
            IntentAccessService intentAccessService,
            IntentPolicyValidator validator,
            PayPalOrdersService ordersService,
            Channel3OfferService channel3OfferService,
            PayPalCheckoutStore checkoutStore,
            IntentDecisionEventStore eventStore) {
        this.intentAccessService = intentAccessService;
        this.validator = validator;
        this.ordersService = ordersService;
        this.channel3OfferService = channel3OfferService;
        this.checkoutStore = checkoutStore;
        this.eventStore = eventStore;
    }

    @PostMapping
    public PayPalOrderResponse createOrder(
            @PathVariable String intentId,
            @RequestBody TransactionProposal proposal,
            HttpServletRequest request) {
        var intent = intentAccessService.findAccessible(intentId, request)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Intent not found"));
        if (intent.currency() == null || intent.currency().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Intent currency is required for PayPal checkout");
        }

        if (proposal.offerEvidence().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Select a priced Channel3 offer before checkout");
        }
        if (proposal.offerEvidence().size() != proposal.items().size()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Every cart item must have verified offer evidence");
        }
        List<OfferEvidence> refreshedEvidence = proposal.offerEvidence().stream()
                .map(channel3OfferService::refresh).toList();
        List<OfferEvidence> unmatchedEvidence = new java.util.ArrayList<>(refreshedEvidence);
        for (TransactionProposal.Item item : proposal.items()) {
            int evidenceIndex = java.util.stream.IntStream.range(0, unmatchedEvidence.size())
                    .filter(index -> matchesItem(item, unmatchedEvidence.get(index), intent.currency()))
                .findFirst().orElse(-1);
            if (evidenceIndex < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Every cart item must match its freshly checked Channel3 offer details");
            }
            unmatchedEvidence.remove(evidenceIndex);
        }
        if (!unmatchedEvidence.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Offer evidence contains items outside the cart");
        }
        TransactionProposal verifiedProposal = new TransactionProposal(
                proposal.proposalId(), proposal.intentId(), proposal.currency(), proposal.items(),
                proposal.shipping(), proposal.tax(), proposal.total(), refreshedEvidence);
        var result = validator.validate(intent, verifiedProposal);
        appendEvent(intentId, proposal.proposalId(), "VALIDATION", result.decision(), result.calculatedTotal(),
            result.violations(), result.explanation(), verifiedProposal);
        if (result.decision() != ValidationDecision.APPROVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only deterministically approved proposals can create PayPal orders");
        }

        PayPalOrderResponse order = ordersService.createOrder(
            intentId, verifiedProposal, result.calculatedTotal(), intent.currency());
        checkoutStore.save(new PayPalCheckoutSession(
            order.orderId(), intentId, verifiedProposal.proposalId(), verifiedProposal, result.calculatedTotal(), intent.currency(),
                PayPalCheckoutSession.State.CREATED, null));
        appendEvent(intentId, proposal.proposalId(), "PAYPAL_ORDER_CREATED", ValidationDecision.APPROVE,
            result.calculatedTotal(), List.of(), "PayPal Sandbox order " + order.orderId()
                + " created with status " + order.status() + "; retailer settlement is not implied", verifiedProposal);
        return order;
    }

    @PostMapping("/{orderId}/capture")
    public PayPalCaptureResponse captureOrder(
            @PathVariable String intentId,
            @PathVariable String orderId,
            HttpServletRequest request) {
        intentAccessService.findAccessible(intentId, request)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Intent not found"));
        PayPalCheckoutSession session = checkoutStore.find(orderId)
                .filter(checkout -> checkout.intentId().equals(intentId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PayPal order not found for this intent"));
        if (session.capture() != null) return session.capture();
        if (!checkoutStore.beginCapture(orderId)) {
            PayPalCheckoutSession current = checkoutStore.find(orderId).orElse(session);
            if (current.capture() != null) return current.capture();
            throw new ResponseStatusException(HttpStatus.CONFLICT, "PayPal order capture is already in progress");
        }

        try {
            PayPalCaptureResponse capture = ordersService.captureOrder(session);
            appendEvent(intentId, session.proposalId(), "PAYPAL_CAPTURE_RESULT", ValidationDecision.APPROVE,
                    capture.amount(), List.of(), "PayPal capture " + capture.captureId() + " returned status " + capture.status(),
                    session.proposal());
            checkoutStore.finishCapture(orderId, capture);
            return capture;
        } catch (RuntimeException exception) {
            checkoutStore.releaseCapture(orderId);
            throw exception;
        }
    }

    private void appendEvent(
            String intentId,
            String proposalId,
            String eventType,
            ValidationDecision decision,
            java.math.BigDecimal amount,
            List<com.intentos.validation.PolicyViolation> violations,
            String explanation,
            TransactionProposal proposalSnapshot) {
        eventStore.append(new IntentDecisionEvent(
                "EVT-" + UUID.randomUUID(), intentId, proposalId, Instant.now(), eventType,
                decision, amount, violations, explanation, proposalSnapshot));
    }

    private static boolean matchesItem(TransactionProposal.Item item, OfferEvidence evidence, String currency) {
        return item.name().equalsIgnoreCase(evidence.productName())
                && item.unitPrice() != null && item.unitPrice().compareTo(evidence.price()) == 0
                && item.merchant() != null && item.merchant().equalsIgnoreCase(evidence.retailer())
                && currency.equalsIgnoreCase(evidence.currency())
                && java.util.Objects.equals(item.category(), evidence.category())
                && item.attributes().equals(evidence.attributes());
    }
}