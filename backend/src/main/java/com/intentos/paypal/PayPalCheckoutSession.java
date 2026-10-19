package com.intentos.paypal;

import com.intentos.intent.TransactionProposal;
import java.math.BigDecimal;

public record PayPalCheckoutSession(
        String orderId,
        String intentId,
        String proposalId,
        TransactionProposal proposal,
        BigDecimal amount,
        String currency,
        State state,
        PayPalCaptureResponse capture,
        String runId,
        String revisionId) {

    public PayPalCheckoutSession(String orderId, String intentId, String proposalId,
            TransactionProposal proposal, BigDecimal amount, String currency, State state,
            PayPalCaptureResponse capture) {
        this(orderId, intentId, proposalId, proposal, amount, currency, state, capture, null, null);
    }

    public enum State {
        CREATED,
        CAPTURING,
        CAPTURED,
        CAPTURE_PENDING,
        CAPTURE_FAILED
    }
}