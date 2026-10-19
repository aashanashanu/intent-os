package com.intentos.shopping;

import com.intentos.intent.IntentAccessService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/intents/{intentId}/shopping-runs")
public class ShoppingRunController {
    private final IntentAccessService access;
    private final ShoppingRunService runs;

    public ShoppingRunController(IntentAccessService access, ShoppingRunService runs) {
        this.access = access;
        this.runs = runs;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShoppingRun create(@PathVariable String intentId, HttpServletRequest request) {
        return runs.createRun(access.findAccessible(intentId, request)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Intent not found")));
    }

    @GetMapping
    public List<ShoppingRun> list(@PathVariable String intentId, HttpServletRequest request) {
        var intent = access.findAccessible(intentId, request)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Intent not found"));
        return runs.findRuns(intent);
    }

    @PostMapping("/{runId}/propose")
    public ShoppingRunService.ProposalResult propose(@PathVariable String intentId, @PathVariable String runId,
            @RequestBody ShoppingRunService.DestinationRequest body, HttpServletRequest request) {
        var intent = access.findAccessible(intentId, request)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Intent not found"));
        return runs.propose(intent, runId, body);
    }

    @GetMapping("/{runId}/revisions")
    public List<ShoppingRevision> revisions(@PathVariable String intentId, @PathVariable String runId,
            HttpServletRequest request) {
        access.findAccessible(intentId, request)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Intent not found"));
        return runs.findRevisions(runId).stream().filter(revision -> revision.intentId().equals(intentId)).toList();
    }

    @PostMapping("/{runId}/revisions")
    @ResponseStatus(HttpStatus.CREATED)
    public ShoppingRevision appendRevision(@PathVariable String intentId, @PathVariable String runId,
            @RequestBody ShoppingRunService.RevisionRequest body, HttpServletRequest request) {
        access.findAccessible(intentId, request)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Intent not found"));
        return runs.appendRevision(intentId, runId, body);
    }

    @PostMapping("/{runId}/revisions/{revisionId}/review")
    public ShoppingReview review(@PathVariable String intentId, @PathVariable String runId,
            @PathVariable String revisionId, HttpServletRequest request) {
        var intent = access.findAccessible(intentId, request)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Intent not found"));
        Object principal = request.getAttribute("paypalUserId");
        String userId = principal instanceof String value ? value : intent.userId();
        return runs.reviewRevision(intentId, runId, revisionId, userId);
    }

    @PostMapping("/{runId}/revisions/{revisionId}/repair")
    public ShoppingRunService.RepairProposalResult repair(@PathVariable String intentId, @PathVariable String runId,
            @PathVariable String revisionId, HttpServletRequest request) {
        var intent = access.findAccessible(intentId, request)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Intent not found"));
        return runs.repairRevision(intent, runId, revisionId);
    }
}