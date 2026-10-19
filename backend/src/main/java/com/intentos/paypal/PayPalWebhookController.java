package com.intentos.paypal;

import com.fasterxml.jackson.databind.JsonNode;
import com.intentos.intent.IntentDecisionEvent;
import com.intentos.intent.IntentDecisionEventStore;
import com.intentos.intent.IntentStore;
import com.intentos.validation.ValidationDecision;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/webhooks/paypal")
public class PayPalWebhookController {
    private static final String CAPTURE_COMPLETED = "PAYMENT.CAPTURE.COMPLETED";
    private final PayPalOrdersService ordersService;
    private final PayPalCheckoutStore checkoutStore;
    private final PayPalWebhookEventStore webhookEventStore;
    private final AgentReceiptStore receiptStore;
    private final IntentStore intentStore;
    private final IntentDecisionEventStore decisionEventStore;

    public PayPalWebhookController(
            PayPalOrdersService ordersService,
            PayPalCheckoutStore checkoutStore,
            PayPalWebhookEventStore webhookEventStore,
            AgentReceiptStore receiptStore,
            IntentStore intentStore,
            IntentDecisionEventStore decisionEventStore) {
        this.ordersService = ordersService;
        this.checkoutStore = checkoutStore;
        this.webhookEventStore = webhookEventStore;
        this.receiptStore = receiptStore;
        this.intentStore = intentStore;
        this.decisionEventStore = decisionEventStore;
    }

    @PostMapping
    public void receive(
            @RequestBody JsonNode event,
            @RequestHeader(value = "PayPal-Transmission-Id", required = false) String transmissionId,
            @RequestHeader(value = "PayPal-Transmission-Time", required = false) String transmissionTime,
            @RequestHeader(value = "PayPal-Transmission-Sig", required = false) String transmissionSignature,
            @RequestHeader(value = "PayPal-Cert-Url", required = false) String certificateUrl,
            @RequestHeader(value = "PayPal-Auth-Algo", required = false) String authAlgorithm) {
        if (!ordersService.verifyWebhook(event, transmissionId, transmissionTime,
                transmissionSignature, certificateUrl, authAlgorithm)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "PayPal webhook signature verification failed");
        }
        String eventId = event.path("id").asText();
        if (eventId.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PayPal webhook event ID is required");
        if (webhookEventStore.isProcessed(eventId)) return;
        if (CAPTURE_COMPLETED.equals(event.path("event_type").asText())) {
            processCaptureCompleted(event);
        }
        webhookEventStore.markProcessedIfAbsent(eventId);
    }

    private void processCaptureCompleted(JsonNode event) {
        JsonNode resource = event.path("resource");
        String orderId = resource.path("supplementary_data").path("related_ids").path("order_id").asText();
        String captureId = resource.path("id").asText();
        String status = resource.path("status").asText();
        String currency = resource.path("amount").path("currency_code").asText();
        BigDecimal amount;
        try {
            amount = new BigDecimal(resource.path("amount").path("value").asText());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PayPal webhook contains an invalid capture amount");
        }
        if (orderId.isBlank() || captureId.isBlank() || !"COMPLETED".equals(status) || currency.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PayPal webhook does not contain completed capture evidence");
        }

        PayPalCheckoutSession checkout = checkoutStore.find(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PayPal checkout order was not found"));
        if (checkout.amount().compareTo(amount) != 0 || !checkout.currency().equals(currency)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "PayPal capture amount does not match approved checkout");
        }
        var intent = intentStore.findById(checkout.intentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Intent contract was not found"));
        PayPalCaptureResponse capture = new PayPalCaptureResponse(orderId, status, captureId, amount, currency);
        checkoutStore.recordWebhookCapture(orderId, capture);

        IntentDecisionEvent webhookEvent = new IntentDecisionEvent(
                "EVT-PAYPAL-" + event.path("id").asText(), checkout.intentId(), checkout.proposalId(),
                Instant.now(), CAPTURE_COMPLETED, ValidationDecision.APPROVE, amount, List.of(),
            "Verified PayPal webhook confirmed capture " + captureId + " as " + status,
            checkout.proposal(), checkout.runId(), checkout.revisionId());
        List<IntentDecisionEvent> timeline = new ArrayList<>(decisionEventStore.findAllByIntentId(checkout.intentId()).stream()
            .filter(item -> checkout.runId() == null
                ? checkout.proposalId().equals(item.proposalId())
                : checkout.runId().equals(item.runId()))
            .toList());
        timeline.add(webhookEvent);
        List<com.intentos.validation.PolicyViolation> violations = timeline.stream()
                .flatMap(item -> item.violations().stream()).distinct().toList();
        List<IntentDecisionEvent> repairs = timeline.stream()
                .filter(item -> item.eventType().startsWith("REPAIR_")).toList();
        BigDecimal originalAmount = timeline.stream()
            .filter(item -> "SHOPPING_VALIDATION".equals(item.eventType())
                    || "VALIDATION".equals(item.eventType()))
            .map(IntentDecisionEvent::calculatedTotal)
            .filter(java.util.Objects::nonNull)
            .findFirst().orElse(amount);
        BigDecimal preventedAmount = originalAmount.subtract(amount).max(BigDecimal.ZERO);
        AgentReceipt receipt = new AgentReceipt(
            "RCPT-" + orderId, checkout.intentId(), intent,
            timeline.stream().map(IntentDecisionEvent::proposalSnapshot).filter(java.util.Objects::nonNull)
                .distinct().toList(), violations,
                repairs, ValidationDecision.APPROVE, amount, preventedAmount, currency, orderId, captureId,
                "COMPLETED", status, event.path("id").asText(), Instant.now(), timeline,
                receiptSearchText(intent, timeline, checkout), checkout.runId(), checkout.revisionId());
        if (receiptStore.saveIfAbsent(receipt)) decisionEventStore.append(webhookEvent);
    }

            private static String receiptSearchText(
                com.intentos.intent.IntentContract intent,
                List<IntentDecisionEvent> timeline,
                PayPalCheckoutSession checkout) {
            StringBuilder text = new StringBuilder(intent.naturalLanguageRequest()).append(' ')
                .append(intent.goal()).append(' ');
            checkout.proposal().items().forEach(item -> text.append(item.name()).append(' ')
                .append(item.category()).append(' '));
            timeline.forEach(item -> {
                text.append(item.eventType()).append(' ').append(item.message()).append(' ');
                item.violations().forEach(violation -> text.append(violation.type()).append(' ')
                    .append(violation.item()).append(' '));
            });
            return text.toString().trim();
            }
}