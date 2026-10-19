package com.intentos.merchant;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulatedMerchantServiceTest {
    @TempDir Path directory;
    private final Instant now = Instant.parse("2026-10-19T10:00:00Z");

    @Test
    void quoteSurvivesServiceRestartAndIsBoundToItsIntent() {
        SimulatedMerchantService service = service("classpath:merchant/catalog.json", now);
        MerchantQuote quote = service.createQuote("INT-1", request());
        assertEquals(MerchantQuote.Status.READY, quote.status());
        assertEquals(quote, service("classpath:merchant/catalog.json", now).verifyQuote("INT-1", quote.quoteId()));
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.findQuote("INT-OTHER", quote.quoteId())).getStatusCode().value());
    }

    @Test
    void expiredQuoteCannotBeVerified() {
        MerchantQuote quote = service("classpath:merchant/catalog.json", now).createQuote("INT-1", request());
        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> service("classpath:merchant/catalog.json", now.plusSeconds(300)).verifyQuote("INT-1", quote.quoteId()));
        assertEquals("MERCHANT_QUOTE_EXPIRED", failure.getReason());
    }

    @Test
    void catalogChangesInvalidatePersistedQuote() throws Exception {
        Path catalog = directory.resolve("catalog.json");
        try (var input = getClass().getResourceAsStream("/merchant/catalog.json")) {
            Files.copy(input, catalog);
        }
        SimulatedMerchantService service = service(catalog.toUri().toString(), now);
        MerchantQuote quote = service.createQuote("INT-1", request());
        Files.writeString(catalog, Files.readString(catalog).replace("780.00", "790.00"));
        assertTrue(service.search("Portable").products().stream()
                .anyMatch(product -> product.price().intValue() == 790));
        assertEquals("MERCHANT_CATALOG_CHANGED_REQUOTE_REQUIRED", assertThrows(ResponseStatusException.class,
                () -> service.verifyQuote("INT-1", quote.quoteId())).getReason());
    }

    private SimulatedMerchantService service(String location, Instant time) {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        return new SimulatedMerchantService(mapper, new DefaultResourceLoader(),
            new FileMerchantQuoteStore(mapper, directory.resolve("quotes")), location, 300,
            Clock.fixed(time, ZoneOffset.UTC));
    }

    private MerchantQuoteRequest request() {
        return new MerchantQuoteRequest(List.of(new MerchantQuoteRequest.Line("portable-computer", "16gb-512gb", 1)),
                "US", "CA", "standard");
    }
}