package com.intentos.repair;

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
@RequestMapping("/api/intents/{intentId}/repair")
public class IntentRepairController {
    private final IntentAccessService intentAccessService;
    private final IntentRepairService repairService;

    public IntentRepairController(IntentAccessService intentAccessService, IntentRepairService repairService) {
        this.intentAccessService = intentAccessService;
        this.repairService = repairService;
    }

    @PostMapping
    public RepairResult repair(@PathVariable String intentId, @RequestBody TransactionProposal proposal, HttpServletRequest request) {
        var intent = intentAccessService.findAccessible(intentId, request)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Intent not found"));
        return repairService.repair(intent, proposal);
    }
}