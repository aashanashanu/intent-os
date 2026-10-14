package com.intentos.health;

import com.intentos.paypal.PayPalCatalogService;
import com.intentos.paypal.Channel3OfferService;
import com.intentos.paypal.ElasticAgentBuilderService;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integrations/status")
public class IntegrationStatusController {
    private final PayPalCatalogService catalogService;
    private final Channel3OfferService channel3OfferService;
    private final ElasticAgentBuilderService agentBuilderService;
    private final boolean elasticEnabled;
    private final boolean authenticationRequired;
    private final boolean paypalWebhookConfigured;

        public IntegrationStatusController(PayPalCatalogService catalogService,
            Channel3OfferService channel3OfferService,
            ElasticAgentBuilderService agentBuilderService,
            @Value("${intentos.elastic.enabled:false}") boolean elasticEnabled,
            @Value("${intentos.auth.required:false}") boolean authenticationRequired,
            @Value("${paypal.webhook-id:}") String paypalWebhookId) {
        this.catalogService = catalogService;
        this.channel3OfferService = channel3OfferService;
        this.agentBuilderService = agentBuilderService;
        this.elasticEnabled = elasticEnabled;
        this.authenticationRequired = authenticationRequired;
        this.paypalWebhookConfigured = !paypalWebhookId.isBlank();
    }

    @GetMapping
    public Map<String, Object> status() {
        return Map.of("paypalCatalogConfigured", catalogService.isConfigured(),
            "channel3Configured", channel3OfferService.isConfigured(),
            "elasticAgentBuilderConfigured", agentBuilderService.isConfigured(),
            "paypalClientId", catalogService.clientId(),
            "paypalWebhookConfigured", paypalWebhookConfigured,
                "elasticPersistenceEnabled", elasticEnabled,
                "authenticationRequired", authenticationRequired);
    }
}
