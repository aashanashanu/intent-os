package com.intentos.intent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class IntentExtractionServiceTest {
    private final IntentLanguageModel languageModel = mock(IntentLanguageModel.class);
    private final IntentExtractionService service = new IntentExtractionService(languageModel);

    @Test
    void delegatesRequestToConfiguredLanguageModel() {
        IntentExtractionResult expected = new IntentExtractionResult(
                "Buy a laptop under $1000", "Laptop", new BigDecimal("1000"), "USD", Set.of("laptop"),
                Map.of(), false, false, false, new BigDecimal("0.9"), false, List.of());
        when(languageModel.extract("Buy a laptop under $1000")).thenReturn(expected);

        IntentExtractionResult result = service.extract("  Buy a laptop under $1000  ");

        assertSame(expected, result);
        verify(languageModel).extract("Buy a laptop under $1000");
    }

    @Test
    void rejectsBlankRequestBeforeCallingModel() {
        assertThrows(ResponseStatusException.class, () -> service.extract("  "));
    }
}
