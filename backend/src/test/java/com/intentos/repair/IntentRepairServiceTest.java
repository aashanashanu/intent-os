package com.intentos.repair;

import com.intentos.intent.IntentContract;
import com.intentos.intent.TransactionProposal;
import com.intentos.intent.TransactionProposal.Item;
import com.intentos.validation.IntentPolicyValidator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}