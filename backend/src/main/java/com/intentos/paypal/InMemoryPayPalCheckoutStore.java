package com.intentos.paypal;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "intentos.elastic.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryPayPalCheckoutStore implements PayPalCheckoutStore {
    private final ConcurrentHashMap<String, PayPalCheckoutSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void save(PayPalCheckoutSession session) {
        sessions.put(session.orderId(), session);
    }

    @Override
    public Optional<PayPalCheckoutSession> find(String orderId) {
        return Optional.ofNullable(sessions.get(orderId));
    }

    @Override
    public synchronized boolean beginCapture(String orderId) {
        PayPalCheckoutSession session = sessions.get(orderId);
        if (session == null || session.state() != PayPalCheckoutSession.State.CREATED) return false;
        sessions.put(orderId, withState(session, PayPalCheckoutSession.State.CAPTURING, null));
        return true;
    }

    @Override
    public synchronized void finishCapture(String orderId, PayPalCaptureResponse capture) {
        PayPalCheckoutSession session = sessions.get(orderId);
        if (session != null && session.state() == PayPalCheckoutSession.State.CAPTURED
                && session.capture() != null && session.capture().equals(capture)) {
            return;
        }
        if (session == null || session.state() != PayPalCheckoutSession.State.CAPTURING) {
            throw new IllegalStateException("PayPal checkout is not being captured");
        }
        PayPalCheckoutSession.State state = switch (capture.status()) {
            case "COMPLETED" -> PayPalCheckoutSession.State.CAPTURED;
            case "PENDING" -> PayPalCheckoutSession.State.CAPTURE_PENDING;
            default -> PayPalCheckoutSession.State.CAPTURE_FAILED;
        };
        sessions.put(orderId, withState(session, state, capture));
    }

    @Override
    public synchronized void recordWebhookCapture(String orderId, PayPalCaptureResponse capture) {
        PayPalCheckoutSession session = sessions.get(orderId);
        if (session == null) throw new IllegalStateException("PayPal checkout session was not found");
        if (session.state() == PayPalCheckoutSession.State.CAPTURED
                && session.capture() != null) {
            if (session.capture().equals(capture)) return;
            throw new IllegalStateException("PayPal order has conflicting completed capture evidence");
        }
        sessions.put(orderId, withState(session, PayPalCheckoutSession.State.CAPTURED, capture));
    }

    @Override
    public synchronized void releaseCapture(String orderId) {
        PayPalCheckoutSession session = sessions.get(orderId);
        if (session != null && session.state() == PayPalCheckoutSession.State.CAPTURING) {
            sessions.put(orderId, withState(session, PayPalCheckoutSession.State.CREATED, null));
        }
    }

    private static PayPalCheckoutSession withState(
            PayPalCheckoutSession session, PayPalCheckoutSession.State state, PayPalCaptureResponse capture) {
        return new PayPalCheckoutSession(session.orderId(), session.intentId(), session.proposalId(),
            session.proposal(), session.amount(), session.currency(), state, capture);
    }
}