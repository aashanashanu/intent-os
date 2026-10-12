package com.intentos.repair;

import com.intentos.validation.ValidationResult;

public record RepairAttempt(int attempt, ValidationResult validation, String action) {
}