package com.intentos.paypal;

import com.intentos.intent.IntentAccessService;
import com.intentos.intent.IntentContract;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentReceiptControllerTest {
    @Test
    void detailRequiresIntentAccessAndReturnsOnlyReceiptFromThatIntent() {
        IntentAccessService access = mock(IntentAccessService.class);
        AgentReceiptStore store = mock(AgentReceiptStore.class);
        AgentReceiptController controller = new AgentReceiptController(access, store);
        IntentContract intent = new IntentContract("INT-1", "USER-1", Instant.now(), "Buy laptop", "Laptop",
                new BigDecimal("1000"), "USD", Set.of(), Map.of(), Map.of(), Set.of(), false,
                false, false, null, Set.of(), Map.of(), null, BigDecimal.ONE, "BUYER_APPROVAL");
        AgentReceipt receipt = new AgentReceipt("RCPT-1", "INT-1", intent, List.of(), List.of(), List.of(),
                com.intentos.validation.ValidationDecision.APPROVE, new BigDecimal("500"), BigDecimal.ZERO,
                "USD", "ORDER-1", "CAPTURE-1", "COMPLETED", "COMPLETED", "WH-1", Instant.now(),
                List.of(), "laptop");
        when(access.findAccessible(org.mockito.ArgumentMatchers.eq("INT-1"), any()))
                .thenReturn(java.util.Optional.of(intent));
        when(store.findAllByIntentId("INT-1")).thenReturn(List.of(receipt));

        assertSame(receipt, controller.get("INT-1", "RCPT-1", new MockHttpServletRequest()));
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> controller.get("INT-1", "RCPT-OTHER", new MockHttpServletRequest()))
                .getStatusCode().value());
        when(access.findAccessible(org.mockito.ArgumentMatchers.eq("INT-2"), any()))
                .thenReturn(java.util.Optional.empty());
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> controller.get("INT-2", "RCPT-1", new MockHttpServletRequest()))
                .getStatusCode().value());
        verify(store, never()).findAllByIntentId("INT-2");
    }
}
