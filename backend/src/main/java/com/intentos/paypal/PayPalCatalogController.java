package com.intentos.paypal;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.Map;

@RestController
@RequestMapping("/api/catalog/products")
public class PayPalCatalogController {
    private final PayPalCatalogService catalogService;

    public PayPalCatalogController(PayPalCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public JsonNode list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be positive and pageSize must be between 1 and 20");
        }
        return catalogService.listProducts(page, pageSize);
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("configured", catalogService.isConfigured(), "readOnly", true);
    }
}