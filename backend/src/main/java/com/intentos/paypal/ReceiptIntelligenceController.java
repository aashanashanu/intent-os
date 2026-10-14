package com.intentos.paypal;

import com.intentos.intent.IntentAccessService;
import com.intentos.intent.IntentMetrics;
import com.intentos.intent.IntentMetricsService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/intents/{intentId}/receipts")
public class ReceiptIntelligenceController {
    private final IntentAccessService accessService;
    private final AgentReceiptStore receiptStore;
    private final IntentMetricsService metricsService;
    private final ReceiptAnalystService analystService;

    public ReceiptIntelligenceController(IntentAccessService accessService, AgentReceiptStore receiptStore,
            IntentMetricsService metricsService, ReceiptAnalystService analystService) {
        this.accessService = accessService;
        this.receiptStore = receiptStore;
        this.metricsService = metricsService;
        this.analystService = analystService;
    }

    @GetMapping("/search")
    public List<AgentReceipt> search(
            @PathVariable String intentId,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "25") int limit,
            HttpServletRequest request) {
        requireAccess(intentId, request);
        if (limit < 1 || limit > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 100");
        }
        if (q.length() > 500 || status.length() > 32) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Receipt search criteria are too long");
        }
        return receiptStore.search(intentId, q, status, limit);
    }

    @GetMapping("/metrics")
    public IntentMetrics metrics(@PathVariable String intentId, HttpServletRequest request) {
        requireAccess(intentId, request);
        return metricsService.calculate(intentId);
    }

    @PostMapping("/analyst")
    public ReceiptAnalystResponse ask(
            @PathVariable String intentId,
            @RequestBody ReceiptAnalystRequest body,
            HttpServletRequest request) {
        requireAccess(intentId, request);
        if (body == null || body.question() == null || body.question().isBlank() || body.question().length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a receipt question up to 500 characters");
        }
        return analystService.answer(intentId, body.question().trim(), body.status());
    }

    private void requireAccess(String intentId, HttpServletRequest request) {
        accessService.findAccessible(intentId, request)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Intent not found"));
    }
}