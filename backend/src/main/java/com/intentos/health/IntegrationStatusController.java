package com.intentos.health;

import com.intentos.paypal.ElasticAgentBuilderService;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integrations/status")
public class IntegrationStatusController {
    private final ElasticAgentBuilderService agentBuilderService;
    private final String paypalClientId;
    private final boolean elasticEnabled;
    private final boolean authenticationRequired;
    private final boolean paypalWebhookConfigured;

    public IntegrationStatusController(ElasticAgentBuilderService agentBuilderService,
            @Value("${paypal.client-id:}") String paypalClientId,
            @Value("${intentos.elastic.enabled:false}") boolean elasticEnabled,
            @Value("${intentos.auth.required:false}") boolean authenticationRequired,
            @Value("${paypal.webhook-id:}") String paypalWebhookId) {
        this.agentBuilderService = agentBuilderService;
        this.paypalClientId = paypalClientId;
        this.elasticEnabled = elasticEnabled;
        this.authenticationRequired = authenticationRequired;
        this.paypalWebhookConfigured = !paypalWebhookId.isBlank();
    }

    @GetMapping
    public Map<String, Object> status() {
        return Map.of("simulatedMerchantConfigured", true,
            "elasticAgentBuilderConfigured", agentBuilderService.isConfigured(),
            "paypalClientId", paypalClientId,
            "paypalWebhookConfigured", paypalWebhookConfigured,
                "elasticPersistenceEnabled", elasticEnabled,
                "authenticationRequired", authenticationRequired);
    }
}
