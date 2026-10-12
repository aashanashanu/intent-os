package com.intentos.health;

import com.intentos.paypal.PayPalCatalogService;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integrations/status")
public class IntegrationStatusController {
    private final PayPalCatalogService catalogService;
    private final boolean elasticEnabled;
    private final boolean authenticationRequired;

    public IntegrationStatusController(PayPalCatalogService catalogService,
            @Value("${intentos.elastic.enabled:false}") boolean elasticEnabled,
            @Value("${intentos.auth.required:false}") boolean authenticationRequired) {
        this.catalogService = catalogService;
        this.elasticEnabled = elasticEnabled;
        this.authenticationRequired = authenticationRequired;
    }

    @GetMapping
    public Map<String, Boolean> status() {
        return Map.of("paypalCatalogConfigured", catalogService.isConfigured(),
                "elasticPersistenceEnabled", elasticEnabled,
                "authenticationRequired", authenticationRequired);
    }
}
