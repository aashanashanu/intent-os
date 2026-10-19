package com.intentos.paypal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intentos.intent.IntentMetricsService;
import com.intentos.intent.IntentLanguageModel;
import com.intentos.intent.IntentModelTask;
import com.intentos.intent.IntentDecisionEvent;
import com.intentos.intent.IntentDecisionEventStore;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReceiptAnalystService {
    private static final String INSTRUCTIONS = """
            Answer the user's question using only the retrieved receipt or decision-event evidence and deterministic metrics provided.
            Do not invent products, retailers, prices, payment outcomes, violations, or causes.
            Distinguish PayPal Sandbox capture from payment to a Channel3 retailer; never claim retailer settlement.
            If the evidence is insufficient, clearly say what cannot be determined.
            Be concise and cite retrieved receipt, run, revision, or event IDs for factual claims. Do not calculate metrics; use supplied metric values only.
            """;
    private final AgentReceiptStore receiptStore;
    private final IntentMetricsService metricsService;
    private final IntentDecisionEventStore decisionEvents;
    private final IntentLanguageModel languageModel;
        private final ElasticAgentBuilderService agentBuilderService;
    private final ObjectMapper objectMapper;

        public ReceiptAnalystService(AgentReceiptStore receiptStore, IntentMetricsService metricsService,
            IntentDecisionEventStore decisionEvents,
            IntentLanguageModel languageModel, ElasticAgentBuilderService agentBuilderService,
            ObjectMapper objectMapper) {
        this.receiptStore = receiptStore;
        this.metricsService = metricsService;
        this.decisionEvents = decisionEvents;
        this.languageModel = languageModel;
        this.agentBuilderService = agentBuilderService;
        this.objectMapper = objectMapper;
    }

    public ReceiptAnalystResponse answer(String intentId, String question, String status) {
        List<AgentReceipt> receipts = receiptStore.search(intentId, question, status, 10);
        List<IntentDecisionEvent> events = decisionEvents.findAllByIntentId(intentId).stream()
            .filter(event -> matchesQuestion(event, question))
            .filter(event -> status == null || status.isBlank() || status.equalsIgnoreCase(event.eventType())
                || status.equalsIgnoreCase(event.decision().name())
                || event.violations().stream().anyMatch(violation -> status.equalsIgnoreCase(violation.type())))
            .limit(50).toList();
        if (receipts.isEmpty() && events.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No matching receipt or decision evidence was found for this intent");
        }
        String evidence;
        try {
            evidence = objectMapper.writeValueAsString(Map.of(
                    "question", question,
                    "intentId", intentId,
                    "deterministicMetrics", metricsService.calculate(intentId),
                        "metricDefinitions", Map.of(
                            "intentComplianceRate", "approved logical runs with no violation on the final validated revision / all evaluated logical runs",
                            "firstPassComplianceRate", "first revision approvals without violations / all evaluated logical runs",
                            "preventedAmount", "original amount minus final approved amount for the same run; unresolved BLOCK/HOLD is excluded",
                            "blockedExposureAmount", "latest unresolved BLOCK/HOLD proposal totals; not savings"),
                        "retrievedReceipts", receipts,
                        "retrievedDecisionEvents", events));
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
                receipts.stream().map(AgentReceipt::receiptId).toList(), receipts.size(),
                events.stream().map(IntentDecisionEvent::eventId).toList(),
                events.stream().map(IntentDecisionEvent::runId).filter(java.util.Objects::nonNull).distinct().toList(),
                events.stream().map(IntentDecisionEvent::revisionId).filter(java.util.Objects::nonNull).distinct().toList(),
                receiptStore.searchMode(),
                agentBuilderService.isConfigured() ? "AGENT_BUILDER_GROUNDED_NARRATION" : "BEDROCK_GROUNDED_NARRATION");
    }

    private static boolean matchesQuestion(IntentDecisionEvent event, String question) {
        String query = question.toLowerCase(java.util.Locale.ROOT);
        String content = (event.eventType() + " " + event.message() + " " + event.proposalId() + " "
                + event.runId() + " " + event.revisionId() + " "
                + event.violations().stream().map(violation -> violation.type() + " " + violation.item()).toList())
                .toLowerCase(java.util.Locale.ROOT);
        return java.util.Arrays.stream(query.split("\\W+")).filter(token -> token.length() > 2).anyMatch(content::contains);
    }
}