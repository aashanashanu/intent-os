package com.intentos.repair;

import com.intentos.validation.ValidationResult;
import com.intentos.intent.TransactionProposal;

public record RepairAttempt(int attempt, ValidationResult validation, String action, TransactionProposal proposal) {
	public RepairAttempt(int attempt, ValidationResult validation, String action) {
		this(attempt, validation, action, null);
	}
}