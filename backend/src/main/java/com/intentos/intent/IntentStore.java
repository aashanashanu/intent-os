package com.intentos.intent;

import java.util.List;
import java.util.Optional;

public interface IntentStore {
    IntentContract save(IntentContract contract);

    Optional<IntentContract> findById(String intentId);

    Optional<IntentContract> findByIdAndUserId(String intentId, String userId);

    List<IntentContract> findAll();

    List<IntentContract> findAllByUserId(String userId);
}