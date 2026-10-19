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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IntentRepairServiceTest {
    private final IntentRepairService repairService = new IntentRepairService(new IntentPolicyValidator());

    @Test
    void removesDisallowedExtrasAndRevalidatesCleanProposal() {
        IntentContract intent = intent(new BigDecimal("1000"), Map.of());
        TransactionProposal proposal = proposal(
                item("Developer Laptop", "899", false, false, false),
                item("Extended Warranty", "199", false, false, true),
                item("Antivirus Subscription", "79", true, true, true));

        RepairResult result = repairService.repair(intent, proposal);

        assertEquals(RepairStatus.APPROVED, result.status());
        assertEquals(1, result.attempts());
        assertEquals(List.of("Developer Laptop"), result.proposal().items().stream().map(Item::name).toList());
        assertEquals(new BigDecimal("971"), result.proposal().total());
    }

    @Test
    void removedItemsAlsoLoseTheirOfferEvidence() {
        IntentContract intent = intent(new BigDecimal("1000"), Map.of());
        Item laptop = item("Developer Laptop", "400", false, false, false);
        Item warranty = item("Extended Warranty", "100", false, false, true);
        Item subscription = item("Antivirus Subscription", "50", true, true, true);
        List<OfferEvidence> evidence = List.of(
                evidence(laptop), evidence(warranty), evidence(subscription));
        TransactionProposal original = new TransactionProposal("PROP-1", "INT-1", "USD",
                List.of(laptop, warranty, subscription), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("550"), evidence);

        RepairResult result = repairService.repair(intent, original);

        assertEquals(RepairStatus.APPROVED, result.status());
        assertEquals(List.of("Developer Laptop"), result.proposal().items().stream().map(Item::name).toList());
        assertEquals(1, result.proposal().offerEvidence().size());
        assertEquals("Developer Laptop", result.proposal().offerEvidence().getFirst().productName());
    }

    @Test
    void clampsQuantityToContractLimit() {
        IntentContract intent = intent(new BigDecimal("1000"), Map.of("Developer Laptop", 1));
        TransactionProposal proposal = proposal(item("Developer Laptop", "400", false, false, false, 2));

        RepairResult result = repairService.repair(intent, proposal);

        assertEquals(RepairStatus.APPROVED, result.status());
        assertEquals(1, result.proposal().items().getFirst().quantity());
    }

    @Test
    void requestsUserReviewIfCoreProductStillExceedsBudget() {
        IntentContract intent = intent(new BigDecimal("500"), Map.of());
        TransactionProposal proposal = proposal(item("Developer Laptop", "899", false, false, false));

        RepairResult result = repairService.repair(intent, proposal);

        assertEquals(RepairStatus.NEEDS_USER_REVIEW, result.status());
        assertEquals(0, result.attempts());
        assertEquals("Developer Laptop", result.proposal().items().getFirst().name());
        assertTrue(result.explanation().contains("user decision"));
    }

    @Test
    void requestsUserReviewForMalformedPriceWithoutCrashing() {
        IntentContract intent = intent(new BigDecimal("1000"), Map.of());
        Item malformed = new Item("Developer Laptop", "laptop", 1, null, false, false, false,
                "Demo Store", Map.of());
        TransactionProposal proposal = new TransactionProposal("PROP-1", "INT-1", "USD", List.of(malformed),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        RepairResult result = repairService.repair(intent, proposal);

        assertEquals(RepairStatus.NEEDS_USER_REVIEW, result.status());
        assertEquals(0, result.attempts());
    }

    @Test
    void stopsAfterThreeRepairPassesAndRequestsUserReview() {
        IntentPolicyValidator validator = mock(IntentPolicyValidator.class);
        AtomicInteger validationCount = new AtomicInteger();
        List<String> addOnNames = List.of("Extra One", "Extra Two", "Extra Three", "Extra Four");
        when(validator.validate(any(IntentContract.class), any(TransactionProposal.class))).thenAnswer(invocation -> {
            String remainingAddOn = addOnNames.get(validationCount.getAndIncrement());
            return new ValidationResult(ValidationDecision.BLOCK, new BigDecimal("552"),
                    List.of(new PolicyViolation("UNREQUESTED_ADDON", remainingAddOn, "requested item", "add-on")),
                    List.of(), "Remove the unrequested add-on.");
        });
        TransactionProposal proposal = proposal(
                item("Developer Laptop", "400", false, false, false),
                item("Extra One", "20", false, false, true),
                item("Extra Two", "20", false, false, true),
                item("Extra Three", "20", false, false, true),
                item("Extra Four", "20", false, false, true));

        RepairResult result = new IntentRepairService(validator).repair(intent(new BigDecimal("1000"), Map.of()), proposal);

        assertEquals(RepairStatus.NEEDS_USER_REVIEW, result.status());
        assertEquals(IntentRepairService.MAX_ATTEMPTS, result.attempts());
        assertEquals(IntentRepairService.MAX_ATTEMPTS + 1, result.history().size());
        assertEquals("Repair limit reached.", result.history().getLast().action());
        assertEquals(4, validationCount.get());
    }

    @Test
    void honorsRemainingPersistedAttemptBudget() {
        IntentContract intent = intent(new BigDecimal("1000"), Map.of());
        TransactionProposal proposal = proposal(item("Developer Laptop", "400", false, false, false),
                item("Unrequested Warranty", "30", false, false, true));

        RepairResult result = repairService.repair(intent, proposal, 1);

        assertEquals(RepairStatus.APPROVED, result.status());
        assertEquals(1, result.attempts());
        assertEquals(List.of("Developer Laptop"), result.proposal().items().stream().map(Item::name).toList());
    }

    private static IntentContract intent(BigDecimal maximum, Map<String, Integer> quantityLimits) {
        return new IntentContract("INT-1", "USER-1", Instant.now(), "Buy a development laptop", "Development laptop",
                maximum, "USD", Set.of("laptop"), Map.of(), Map.of(), Set.of(), false, false, false,
                maximum, Set.of(), quantityLimits, null, new BigDecimal("0.94"), "BUYER_APPROVAL");
    }

    private static TransactionProposal proposal(Item... items) {
        BigDecimal itemTotal = List.of(items).stream().map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tax = new BigDecimal("72");
        return new TransactionProposal("PROP-1", "INT-1", "USD", List.of(items), BigDecimal.ZERO, tax, itemTotal.add(tax));
    }

    private static Item item(String name, String price, boolean recurring, boolean subscription, boolean addOn) {
        return item(name, price, recurring, subscription, addOn, 1);
    }

    private static Item item(String name, String price, boolean recurring, boolean subscription, boolean addOn, int quantity) {
        return new Item(name, "laptop", quantity, new BigDecimal(price), recurring, subscription, addOn,
                "Demo Store", Map.of());
    }

    private static OfferEvidence evidence(Item item) {
        return new OfferEvidence("test", item.name(), item.name(), item.category(), item.merchant(), null,
                item.unitPrice(), "USD", "IN_STOCK", item.attributes(), Instant.now());
    }
}