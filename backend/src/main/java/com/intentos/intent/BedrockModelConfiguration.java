package com.intentos.intent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

@Configuration
@ConditionalOnProperty(name = "intentos.llm.enabled", havingValue = "true")
public class BedrockModelConfiguration {
    @Bean
    BedrockRuntimeClient bedrockRuntimeClient(@Value("${intentos.llm.region:us-east-1}") String region) {
        return BedrockRuntimeClient.builder()
                .region(Region.of(region))
                .build();
    }
}
