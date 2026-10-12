package com.intentos.validation;

import java.math.BigDecimal;
import java.util.List;

public record ValidationResult(
        ValidationDecision decision,
        BigDecimal calculatedTotal,
        List<PolicyViolation> violations,
        List<String> recommendations,
        String explanation) {

    public ValidationResult {
        violations = List.copyOf(violations);
        recommendations = List.copyOf(recommendations);
    }
}