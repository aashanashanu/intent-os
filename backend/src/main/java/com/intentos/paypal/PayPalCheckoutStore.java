package com.intentos.paypal;

import java.util.Optional;

public interface PayPalCheckoutStore {
    void save(PayPalCheckoutSession session);

    Optional<PayPalCheckoutSession> find(String orderId);

    boolean beginCapture(String orderId);

    void finishCapture(String orderId, PayPalCaptureResponse capture);

    void recordWebhookCapture(String orderId, PayPalCaptureResponse capture);

    void releaseCapture(String orderId);
}