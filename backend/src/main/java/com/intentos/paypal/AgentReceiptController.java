package com.intentos.paypal;

import com.intentos.intent.IntentAccessService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/intents/{intentId}/receipts")
public class AgentReceiptController {
    private final IntentAccessService intentAccessService;
    private final AgentReceiptStore receiptStore;

    public AgentReceiptController(IntentAccessService intentAccessService, AgentReceiptStore receiptStore) {
        this.intentAccessService = intentAccessService;
        this.receiptStore = receiptStore;
    }

    @GetMapping
    public List<AgentReceipt> list(@PathVariable String intentId, HttpServletRequest request) {
        intentAccessService.findAccessible(intentId, request)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Intent not found"));
        return receiptStore.findAllByIntentId(intentId);
    }

    @GetMapping("/{receiptId}")
    public AgentReceipt get(@PathVariable String intentId, @PathVariable String receiptId,
            HttpServletRequest request) {
        intentAccessService.findAccessible(intentId, request)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Intent not found"));
        return receiptStore.findAllByIntentId(intentId).stream()
                .filter(receipt -> receipt.receiptId().equals(receiptId))
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AgentReceipt not found"));
    }
}