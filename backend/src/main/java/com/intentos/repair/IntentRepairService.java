package com.intentos.repair;

import com.intentos.intent.IntentContract;
import com.intentos.intent.OfferEvidence;
import com.intentos.intent.TransactionProposal;
import com.intentos.intent.TransactionProposal.Item;
import com.intentos.validation.IntentPolicyValidator;
import com.intentos.validation.PolicyViolation;
import com.intentos.validation.ValidationDecision;
import com.intentos.validation.ValidationResult;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class IntentRepairService {
    public static final int MAX_ATTEMPTS = 3;
    private static final Set<String> REMOVABLE_VIOLATIONS = Set.of(
            "RECURRING_PAYMENT",
            "SUBSCRIPTION_NOT_ALLOWED",
            "UNREQUESTED_ADDON",
            "PROHIBITED_ITEM",
            "MERCHANT_RESTRICTED",
            "CATEGORY_NOT_ALLOWED");
    private static final Set<String> SAFELY_REPAIRABLE_VIOLATIONS = Set.of(
            "BUDGET_EXCEEDED",
            "TOTAL_MISMATCH",
            "QUANTITY_EXCEEDED",
            "RECURRING_PAYMENT",
            "SUBSCRIPTION_NOT_ALLOWED",
            "UNREQUESTED_ADDON",
            "PROHIBITED_ITEM",
            "MERCHANT_RESTRICTED",
            "CATEGORY_NOT_ALLOWED");

    private final IntentPolicyValidator validator;

    public IntentRepairService(IntentPolicyValidator validator) {
        this.validator = validator;
    }

    public RepairResult repair(IntentContract intent, TransactionProposal initialProposal) {
        return repair(intent, initialProposal, MAX_ATTEMPTS);
    }

    public RepairResult repair(IntentContract intent, TransactionProposal initialProposal, int maxAttempts) {
        if (maxAttempts < 1 || maxAttempts > MAX_ATTEMPTS) {
            throw new IllegalArgumentException("Repair attempt budget must be between 1 and " + MAX_ATTEMPTS);
        }
        TransactionProposal proposal = initialProposal;
        List<RepairAttempt> history = new ArrayList<>();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            ValidationResult result = validator.validate(intent, proposal);
            if (result.decision() == ValidationDecision.APPROVE) {
                history.add(new RepairAttempt(attempt, result, "Proposal satisfies intent policies.", proposal));
                return new RepairResult(RepairStatus.APPROVED, attempt - 1, proposal, history,
                        "Proposal approved after deterministic policy validation.");
            }

            if (result.violations().stream().anyMatch(violation -> !SAFELY_REPAIRABLE_VIOLATIONS.contains(violation.type()))) {
                history.add(new RepairAttempt(attempt, result, "A violation requires a new proposal or user decision.", proposal));
                return new RepairResult(RepairStatus.NEEDS_USER_REVIEW, attempt - 1, proposal, history,
                        "The proposal contains a violation that cannot be corrected safely without changing user intent.");
            }

            TransactionProposal repaired = applySafeCorrections(intent, proposal, result);
            if (repaired == null || sameProposal(proposal, repaired)) {
                history.add(new RepairAttempt(attempt, result, "No safe automatic correction is available.", proposal));
                return new RepairResult(RepairStatus.NEEDS_USER_REVIEW, attempt - 1, proposal, history,
                        "Policy violations require a user decision or a new agent proposal.");
            }

            history.add(new RepairAttempt(attempt, result, "Applied deterministic policy-safe corrections.", proposal));
            proposal = repaired;
        }

        ValidationResult finalResult = validator.validate(intent, proposal);
        history.add(new RepairAttempt(maxAttempts, finalResult, "Repair limit reached.", proposal));
        if (finalResult.decision() == ValidationDecision.APPROVE) {
            return new RepairResult(RepairStatus.APPROVED, maxAttempts, proposal, history,
                    "Proposal approved on the final repair attempt.");
        }
        return new RepairResult(RepairStatus.NEEDS_USER_REVIEW, maxAttempts, proposal, history,
                "Maximum repair attempts reached; user review is required.");
    }

    private TransactionProposal applySafeCorrections(
            IntentContract intent,
            TransactionProposal proposal,
            ValidationResult validation) {
        Set<String> removals = new HashSet<>();
        for (PolicyViolation violation : validation.violations()) {
            if (violation.item() != null && REMOVABLE_VIOLATIONS.contains(violation.type())) {
                removals.add(violation.item());
            }
        }

        List<Item> items = new ArrayList<>();
        for (Item item : proposal.items()) {
            if (removals.stream().anyMatch(name -> name.equalsIgnoreCase(item.name()))) continue;
            Integer maxQuantity = quantityLimit(intent, item.name());
            if (maxQuantity != null && item.quantity() > maxQuantity) {
                items.add(copyWithQuantity(item, maxQuantity));
            } else {
                items.add(item);
            }
        }

        BigDecimal maximum = effectiveBudget(intent);
        if (maximum != null && total(items, proposal.shipping(), proposal.tax()).compareTo(maximum) > 0) {
            List<Item> optionalItems = items.stream()
                    .filter(Item::addOn)
                    .sorted(Comparator.comparing(Item::unitPrice).reversed())
                    .toList();
            for (Item optional : optionalItems) {
                items.remove(optional);
                if (total(items, proposal.shipping(), proposal.tax()).compareTo(maximum) <= 0) break;
            }
        }

        BigDecimal correctedTotal = total(items, proposal.shipping(), proposal.tax());
        List<OfferEvidence> remainingEvidence = new ArrayList<>(proposal.offerEvidence());
        List<OfferEvidence> currentEvidence = new ArrayList<>();
        for (Item item : items) {
            int match = java.util.stream.IntStream.range(0, remainingEvidence.size())
                    .filter(index -> matches(item, remainingEvidence.get(index)))
                    .findFirst().orElse(-1);
            if (match >= 0) currentEvidence.add(remainingEvidence.remove(match));
        }
        TransactionProposal corrected = new TransactionProposal(
                proposal.proposalId(), proposal.intentId(), proposal.currency(), items,
            proposal.shipping(), proposal.tax(), correctedTotal, currentEvidence);
        return corrected.equals(proposal) ? null : corrected;
    }

    private static boolean matches(Item item, OfferEvidence evidence) {
        return item.name().equalsIgnoreCase(evidence.productName())
                && java.util.Objects.equals(item.category(), evidence.category())
                && item.unitPrice() != null && item.unitPrice().compareTo(evidence.price()) == 0
                && java.util.Objects.equals(item.merchant(), evidence.retailer())
                && item.attributes().equals(evidence.attributes());
    }

    private static BigDecimal effectiveBudget(IntentContract intent) {
        BigDecimal budget = intent.maxAmount();
        if (intent.maximumTransactionAmount() != null
                && (budget == null || intent.maximumTransactionAmount().compareTo(budget) < 0)) {
            budget = intent.maximumTransactionAmount();
        }
        return budget;
    }

    private static BigDecimal total(List<Item> items, BigDecimal shipping, BigDecimal tax) {
        BigDecimal itemTotal = items.stream()
                .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return itemTotal.add(shipping == null ? BigDecimal.ZERO : shipping)
                .add(tax == null ? BigDecimal.ZERO : tax);
    }

    private static Integer quantityLimit(IntentContract intent, String name) {
        return intent.maximumQuantities().entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(java.util.Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private static Item copyWithQuantity(Item item, int quantity) {
        return new Item(item.name(), item.category(), quantity, item.unitPrice(), item.recurring(), item.subscription(),
                item.addOn(), item.merchant(), item.attributes());
    }

    private static boolean sameProposal(TransactionProposal left, TransactionProposal right) {
        return left.equals(right);
    }
}