package com.intentos.shopping;

import java.time.Instant;

public record ShoppingRun(String runId, String intentId, Instant createdAt) {}