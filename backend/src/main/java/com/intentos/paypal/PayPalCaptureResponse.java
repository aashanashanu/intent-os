package com.intentos.paypal;

import java.math.BigDecimal;

public record PayPalCaptureResponse(
                String orderId, String status, String captureId, BigDecimal amount, String currency) {
        public PayPalCaptureResponse {
                if (amount != null) amount = amount.stripTrailingZeros();
        }
}