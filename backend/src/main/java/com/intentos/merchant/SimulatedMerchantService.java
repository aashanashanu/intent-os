package com.intentos.merchant;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SimulatedMerchantService {
    private final ObjectMapper objectMapper;
    private final ResourceLoader resources;
    private final String catalogLocation;
    private final MerchantQuoteStore quotes;
    private final long quoteTtlSeconds;
    private final Clock clock;
    private final MerchantQuoteCalculator calculator = new MerchantQuoteCalculator();

    @Autowired
    public SimulatedMerchantService(ObjectMapper objectMapper, ResourceLoader resources,
            MerchantQuoteStore quotes,
            @Value("${intentos.merchant.catalog:classpath:merchant/catalog.json}") String catalogLocation,
            @Value("${intentos.merchant.quote-ttl-seconds:300}") long quoteTtlSeconds) {
        this(objectMapper, resources, quotes, catalogLocation, quoteTtlSeconds, Clock.systemUTC());
    }

    SimulatedMerchantService(ObjectMapper objectMapper, ResourceLoader resources, MerchantQuoteStore quotes,
            String catalogLocation, long quoteTtlSeconds, Clock clock) {
        if (quoteTtlSeconds < 1 || quoteTtlSeconds > 3600) {
            throw new IllegalArgumentException("Merchant quote TTL must be between 1 and 3600 seconds");
        }
        this.objectMapper = objectMapper;
        this.resources = resources;
        this.quotes = quotes;
        this.catalogLocation = catalogLocation;
        this.quoteTtlSeconds = quoteTtlSeconds;
        this.clock = clock;
    }

    public CatalogResponse search(String query) {
        if (query.length() > 300) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Merchant query is too long");
        Snapshot snapshot = loadCatalog();
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        List<MerchantCatalog.Product> products = snapshot.catalog().products().stream()
                .filter(product -> normalized.isEmpty() || searchable(product).contains(normalized))
                .limit(50).toList();
        return new CatalogResponse("simulated-merchant", true, snapshot.catalog().merchantId(),
                snapshot.catalog().version(), snapshot.fingerprint(), snapshot.catalog().currency(),
                products, snapshot.catalog().destinationRules(), clock.instant(), MerchantQuoteCalculator.DISCLOSURE);
    }

    public MerchantCatalog.Product product(String productId, String variantId) {
        return loadCatalog().catalog().products().stream()
                .filter(product -> product.productId().equals(productId) && product.variantId().equals(variantId))
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Merchant product not found"));
    }

    public MerchantQuote createQuote(String intentId, MerchantQuoteRequest request) {
        if (request == null || request.lines().size() > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A quote request may contain at most 50 lines");
        }
        Snapshot snapshot = loadCatalog();
        Instant createdAt = clock.instant();
        MerchantQuote quote = calculator.calculate("MQ-" + UUID.randomUUID(), intentId,
                snapshot.catalog(), snapshot.fingerprint(), request, createdAt, createdAt.plusSeconds(quoteTtlSeconds));
        return quotes.save(quote);
    }

    public MerchantQuote findQuote(String intentId, String quoteId) {
        if (!quoteId.matches("MQ-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Merchant quote not found");
        }
        MerchantQuote quote = quotes.find(quoteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Merchant quote not found"));
        if (!intentId.equals(quote.intentId())) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Merchant quote not found");
        return quote;
    }

    public MerchantQuote verifyQuote(String intentId, String quoteId) {
        MerchantQuote quote = findQuote(intentId, quoteId);
        if (quote.status() != MerchantQuote.Status.READY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "MERCHANT_QUOTE_INCOMPLETE");
        }
        verifyQuoteEvidence(quote);
        return quote;
    }

    public MerchantQuote verifyQuoteEvidence(String intentId, String quoteId) {
        MerchantQuote quote = findQuote(intentId, quoteId);
        verifyQuoteEvidence(quote);
        return quote;
    }

    private void verifyQuoteEvidence(MerchantQuote quote) {
        if (!quote.expiresAt().isAfter(clock.instant())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "MERCHANT_QUOTE_EXPIRED");
        }
        if (!loadCatalog().fingerprint().equals(quote.catalogFingerprint())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "MERCHANT_CATALOG_CHANGED_REQUOTE_REQUIRED");
        }
    }

    private Snapshot loadCatalog() {
        try (var input = resources.getResource(catalogLocation).getInputStream()) {
            byte[] bytes = input.readAllBytes();
            MerchantCatalog catalog = objectMapper.readValue(bytes, MerchantCatalog.class);
            validateCatalog(catalog);
            return new Snapshot(catalog, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        } catch (IOException | NoSuchAlgorithmException | IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Merchant catalog is unavailable or invalid", exception);
        }
    }

    private static void validateCatalog(MerchantCatalog catalog) {
        if (catalog.merchantId() == null || catalog.merchantId().isBlank()
                || catalog.version() == null || catalog.version().isBlank()) {
            throw new IllegalArgumentException("Merchant identity and catalog version are required");
        }
        Set<String> productKeys = new HashSet<>();
        for (MerchantCatalog.Product product : catalog.products()) {
            if (product.productId() == null || product.productId().isBlank()
                    || product.variantId() == null || product.variantId().isBlank()
                    || product.name() == null || product.name().isBlank()
                    || !productKeys.add(product.productId() + ":" + product.variantId())) {
                throw new IllegalArgumentException("Product/variant identities must be present and unique");
            }
        }
        Set<String> ruleKeys = new HashSet<>();
        for (MerchantCatalog.DestinationRule rule : catalog.destinationRules()) {
            String key = (rule.country() + ":" + rule.region() + ":" + rule.shippingMethod()).toUpperCase(Locale.ROOT);
            if (!ruleKeys.add(key)) throw new IllegalArgumentException("Destination rules must be unique");
        }
    }

    private static String searchable(MerchantCatalog.Product product) {
        return (product.name() + " " + product.category() + " " + product.attributes()).toLowerCase(Locale.ROOT);
    }

    private record Snapshot(MerchantCatalog catalog, String fingerprint) {}

    public record CatalogResponse(String source, boolean simulation, String merchantId, String catalogVersion,
            String catalogFingerprint, String currency, List<MerchantCatalog.Product> products,
            List<MerchantCatalog.DestinationRule> destinationRules, Instant retrievedAt, String disclosure) {}
}