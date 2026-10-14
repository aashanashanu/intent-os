package com.intentos.intent;

import java.math.BigDecimal;

public record IntentMetrics(
        int totalProposals,
        int approvedProposals,
        int violatingProposals,
        int repairedProposals,
        int proposalsRequiringReview,
        BigDecimal preventedAmount,
        BigDecimal capturedAmount,
        BigDecimal intentComplianceRate,
        BigDecimal violationRate,
        BigDecimal repairRate,
        BigDecimal humanInterventionRate) {}