package com.intentos.paypal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intentos.intent.IntentMetricsService;
import com.intentos.intent.IntentLanguageModel;
import com.intentos.intent.IntentModelTask;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReceiptAnalystService {
    private static final String INSTRUCTIONS = """
            Answer the user's question using only the retrieved AgentReceipt evidence and deterministic metrics provided.
            Do not invent products, retailers, prices, payment outcomes, violations, or causes.
            Distinguish PayPal Sandbox capture from payment to a Channel3 retailer; never claim retailer settlement.
            If the evidence is insufficient, clearly say what cannot be determined.
            Be concise and cite receipt IDs for factual claims. Do not calculate metrics; use supplied metric values only.
            """;
    private final AgentReceiptStore receiptStore;
    private final IntentMetricsService metricsService;
    private final IntentLanguageModel languageModel;
        private final ElasticAgentBuilderService agentBuilderService;
    private final ObjectMapper objectMapper;

    public ReceiptAnalystService(AgentReceiptStore receiptStore, IntentMetricsService metricsService,
            IntentLanguageModel languageModel, ElasticAgentBuilderService agentBuilderService,
            ObjectMapper objectMapper) {
        this.receiptStore = receiptStore;
        this.metricsService = metricsService;
        this.languageModel = languageModel;
        this.agentBuilderService = agentBuilderService;
        this.objectMapper = objectMapper;
    }

    public ReceiptAnalystResponse answer(String intentId, String question, String status) {
        List<AgentReceipt> receipts = receiptStore.search(intentId, question, status, 10);
        if (receipts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No matching AgentReceipt evidence was found for this intent");
        }
        String evidence;
        try {
            evidence = objectMapper.writeValueAsString(Map.of(
                    "question", question,
                    "intentId", intentId,
                    "deterministicMetrics", metricsService.calculate(intentId),
                    "retrievedReceipts", receipts));
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not prepare receipt evidence", exception);
        }
        String answer = agentBuilderService.isConfigured()
                ? agentBuilderService.answer(INSTRUCTIONS + "\nEvidence JSON:\n" + evidence)
                : languageModel.generateAdvisory(IntentModelTask.EXPLANATION, INSTRUCTIONS, evidence)
                        .filter(text -> !text.isBlank())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                                "Receipt Analyst model is not configured"));
        return new ReceiptAnalystResponse(answer,
                receipts.stream().map(AgentReceipt::receiptId).toList(), receipts.size());
    }
}