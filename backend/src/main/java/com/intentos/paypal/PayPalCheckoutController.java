package com.intentos.paypal;

import com.intentos.intent.IntentAccessService;
import com.intentos.intent.IntentDecisionEvent;
import com.intentos.intent.IntentDecisionEventStore;
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
    private final PayPalCheckoutStore checkoutStore;
    private final IntentDecisionEventStore eventStore;

    public PayPalCheckoutController(
            IntentAccessService intentAccessService,
            IntentPolicyValidator validator,
            PayPalOrdersService ordersService,
            PayPalCheckoutStore checkoutStore,
            IntentDecisionEventStore eventStore) {
        this.intentAccessService = intentAccessService;
        this.validator = validator;
        this.ordersService = ordersService;
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

        var result = validator.validate(intent, proposal);
        appendEvent(intentId, proposal.proposalId(), "VALIDATION", result.decision(), result.calculatedTotal(),
            result.violations(), result.explanation(), proposal);
        if (result.decision() != ValidationDecision.APPROVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only deterministically approved proposals can create PayPal orders");
        }

        PayPalOrderResponse order = ordersService.createOrder(
                intentId, proposal, result.calculatedTotal(), intent.currency());
        checkoutStore.save(new PayPalCheckoutSession(
            order.orderId(), intentId, proposal.proposalId(), proposal, result.calculatedTotal(), intent.currency(),
                PayPalCheckoutSession.State.CREATED, null));
        appendEvent(intentId, proposal.proposalId(), "PAYPAL_ORDER_CREATED", ValidationDecision.APPROVE,
            result.calculatedTotal(), List.of(), "PayPal order " + order.orderId() + " created with status " + order.status(), proposal);
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
}