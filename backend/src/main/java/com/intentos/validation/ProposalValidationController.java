package com.intentos.validation;

import com.intentos.intent.IntentAccessService;
import com.intentos.intent.TransactionProposal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/intents/{intentId}/validation")
public class ProposalValidationController {
    private final IntentAccessService intentAccessService;
    private final IntentPolicyValidator validator;

    public ProposalValidationController(IntentAccessService intentAccessService, IntentPolicyValidator validator) {
        this.intentAccessService = intentAccessService;
        this.validator = validator;
    }

    @PostMapping
    public ValidationResult validate(@PathVariable String intentId, @RequestBody TransactionProposal proposal, HttpServletRequest request) {
        var intent = intentAccessService.findAccessible(intentId, request)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Intent not found"));
        return validator.validate(intent, proposal);
    }
}