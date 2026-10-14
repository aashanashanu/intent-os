package com.intentos.intent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@SpringBootTest(properties = {
        "intentos.elastic.enabled=true",
        "intentos.elastic.url=https://elastic.test",
        "intentos.elastic.api-key=dummy-test-key",
        "intentos.llm.enabled=false",
        "intentos.elastic.kibana-url=",
        "intentos.elastic.agent-builder.api-key="
})
class ElasticEnabledStartupTest {
    @Autowired
    private IntentDecisionEventStore eventStore;

    @Test
    void bootsWithTheProductionElasticEventStore() {
        assertInstanceOf(ElasticsearchIntentDecisionEventStore.class, eventStore);
    }
}