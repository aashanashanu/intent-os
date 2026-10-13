package com.intentos.paypal;

public record PayPalOrderResponse(String orderId, String status, String approvalUrl) {}