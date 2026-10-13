package com.intentos.repair;

import com.intentos.intent.TransactionProposal;
import java.util.List;

public record RepairResult(
        RepairStatus status,
        int attempts,
        TransactionProposal proposal,
        List<RepairAttempt> history,
        String explanation,
        String modelAdvice) {

    public RepairResult(
            RepairStatus status,
            int attempts,
            TransactionProposal proposal,
            List<RepairAttempt> history,
            String explanation) {
        this(status, attempts, proposal, history, explanation, null);
    }

    public RepairResult {
        history = List.copyOf(history);
    }
}