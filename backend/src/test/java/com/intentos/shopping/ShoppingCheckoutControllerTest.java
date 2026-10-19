package com.intentos.shopping;

import com.intentos.intent.IntentAccessService;
import com.intentos.intent.IntentContract;
import com.intentos.intent.IntentDecisionEventStore;
import com.intentos.merchant.SimulatedMerchantService;
import com.intentos.paypal.PayPalCheckoutStore;
import com.intentos.paypal.PayPalOrdersService;
import com.intentos.validation.IntentPolicyValidator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShoppingCheckoutControllerTest {
    private final IntentAccessService access = mock(IntentAccessService.class);
    private final ShoppingRunService runs = mock(ShoppingRunService.class);
    private final SimulatedMerchantService merchant = mock(SimulatedMerchantService.class);
    private final IntentPolicyValidator validator = mock(IntentPolicyValidator.class);
    private final PayPalOrdersService orders = mock(PayPalOrdersService.class);
    private final PayPalCheckoutStore checkouts = mock(PayPalCheckoutStore.class);
    private final IntentDecisionEventStore events = mock(IntentDecisionEventStore.class);
    private final ShoppingCheckoutController controller = new ShoppingCheckoutController(
            access, runs, merchant, validator, orders, checkouts, events);

    @Test
    void approvedRevisionCannotCreateOrderWithoutOwnerBoundFinalReview() {
        IntentContract intent = new IntentContract("INT-1", "USER-1", Instant.now(), "Buy laptop", "Laptop",
                new BigDecimal("1000"), "USD", Set.of(), Map.of(), Map.of(), Set.of(), false,
                false, false, null, Set.of(), Map.of(), null, BigDecimal.ONE, "BUYER_APPROVAL");
        ShoppingRevision revision = new ShoppingRevision("RUN-1-R1", "PROP-RUN-1-R1", "RUN-1", "INT-1", 1,
                null, Instant.now(), "MQ-1", List.of(new ShoppingRevision.CartItem("ITEM-1", "product", "variant", 1)),
                List.of("product:variant"), "selected", "nova-lite", "SHOPPING", "APPROVE", List.of(), null, 0, null);
        when(access.findAccessible(org.mockito.ArgumentMatchers.eq("INT-1"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Optional.of(intent));
        when(runs.findRevisions("RUN-1")).thenReturn(List.of(revision));
        when(runs.findReview("RUN-1", revision.revisionId(), "USER-1"))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                        "Final review is required for this revision"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("paypalUserId", "USER-1");

        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> controller.create("INT-1", "RUN-1", revision.revisionId(), request));

        assertEquals(409, failure.getStatusCode().value());
        verify(orders, never()).createOrder(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString());
    }
}
