package com.intentos.repair;

import com.intentos.intent.IntentAccessService;
import com.intentos.intent.IntentDecisionEvent;
import com.intentos.intent.IntentDecisionEventStore;
import com.intentos.intent.IntentLanguageModel;
import com.intentos.intent.IntentModelTask;
import com.intentos.intent.TransactionProposal;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.UUID;
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
    private final IntentDecisionEventStore eventStore;
    private final IntentLanguageModel languageModel;

    public IntentRepairController(
            IntentAccessService intentAccessService,
            IntentRepairService repairService,
            IntentDecisionEventStore eventStore,
            IntentLanguageModel languageModel) {
        this.intentAccessService = intentAccessService;
        this.repairService = repairService;
        this.eventStore = eventStore;
        this.languageModel = languageModel;
    }

    @PostMapping
    public RepairResult repair(@PathVariable String intentId, @RequestBody TransactionProposal proposal, HttpServletRequest request) {
        var intent = intentAccessService.findAccessible(intentId, request)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Intent not found"));
        RepairResult result = repairService.repair(intent, proposal);
        if (result.attempts() > 0 || result.status() == RepairStatus.NEEDS_USER_REVIEW) {
            String evidence = "Intent goal: " + intent.goal()
                    + "\nPolicy findings: " + result.history().stream()
                            .flatMap(attempt -> attempt.validation().violations().stream())
                            .map(violation -> violation.type() + " on " + violation.item())
                            .distinct().toList()
                    + "\nDeterministic result: " + result.status()
                    + "\nFinal cart: " + result.proposal().items().stream()
                            .map(item -> item.quantity() + " x " + item.name() + " at " + item.unitPrice())
                            .toList();
            try {
                String advice = languageModel.generateAdvisory(IntentModelTask.REPAIR,
                        "Suggest concise alternative cart changes that respect the supplied intent and policy findings. "
                                + "Do not claim approval, alter totals, or authorize payment. Return advice only; the server has already made the deterministic repair decision.",
                        evidence).filter(text -> !text.isBlank()).orElse(null);
                if (advice != null) {
                    result = new RepairResult(result.status(), result.attempts(), result.proposal(), result.history(),
                            result.explanation(), advice);
                }
            } catch (RuntimeException ignored) {
                result = new RepairResult(result.status(), result.attempts(), result.proposal(), result.history(),
                        result.explanation(), null);
            }
        }
        for (RepairAttempt attempt : result.history()) {
            eventStore.append(new IntentDecisionEvent(
                    "EVT-" + UUID.randomUUID(), intentId, proposal.proposalId(), Instant.now(), "REPAIR_ATTEMPT",
                    attempt.validation().decision(), attempt.validation().calculatedTotal(),
                    attempt.validation().violations(), attempt.action()));
        }
        RepairAttempt lastAttempt = result.history().get(result.history().size() - 1);
        eventStore.append(new IntentDecisionEvent(
                "EVT-" + UUID.randomUUID(), intentId, proposal.proposalId(), Instant.now(), "REPAIR_COMPLETED",
                lastAttempt.validation().decision(), lastAttempt.validation().calculatedTotal(),
                lastAttempt.validation().violations(), result.explanation()));
        return result;
    }
}