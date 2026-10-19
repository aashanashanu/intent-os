package com.intentos.merchant;

import com.intentos.intent.IntentAccessService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/intents/{intentId}/merchant")
public class SimulatedMerchantController {
    private final IntentAccessService accessService;
    private final SimulatedMerchantService merchant;

    public SimulatedMerchantController(IntentAccessService accessService, SimulatedMerchantService merchant) {
        this.accessService = accessService;
        this.merchant = merchant;
    }

    @GetMapping("/products")
    public SimulatedMerchantService.CatalogResponse search(@PathVariable String intentId,
            @RequestParam(defaultValue = "") String q, HttpServletRequest request) {
        requireAccess(intentId, request);
        return merchant.search(q);
    }

    @GetMapping("/products/{productId}/variants/{variantId}")
    public MerchantCatalog.Product detail(@PathVariable String intentId, @PathVariable String productId,
            @PathVariable String variantId, HttpServletRequest request) {
        requireAccess(intentId, request);
        return merchant.product(productId, variantId);
    }

    @PostMapping("/quotes")
    public MerchantQuote quote(@PathVariable String intentId, @RequestBody MerchantQuoteRequest body,
            HttpServletRequest request) {
        requireAccess(intentId, request);
        return merchant.createQuote(intentId, body);
    }

    @GetMapping("/quotes/{quoteId}")
    public MerchantQuote find(@PathVariable String intentId, @PathVariable String quoteId, HttpServletRequest request) {
        requireAccess(intentId, request);
        return merchant.findQuote(intentId, quoteId);
    }

    @PostMapping("/quotes/{quoteId}/verify")
    public MerchantQuote verify(@PathVariable String intentId, @PathVariable String quoteId, HttpServletRequest request) {
        requireAccess(intentId, request);
        return merchant.verifyQuote(intentId, quoteId);
    }

    private void requireAccess(String intentId, HttpServletRequest request) {
        accessService.findAccessible(intentId, request)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Intent not found"));
    }
}