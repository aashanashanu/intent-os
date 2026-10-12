package com.intentos.repair;

import com.intentos.intent.TransactionProposal;
import java.util.List;

public record RepairResult(
        RepairStatus status,
        int attempts,
        TransactionProposal proposal,
        List<RepairAttempt> history,
        String explanation) {

    public RepairResult {
        history = List.copyOf(history);
    }
}