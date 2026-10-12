package com.intentos.intent;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class IntentAccessService {
    private final IntentStore intentStore;

    public IntentAccessService(IntentStore intentStore) {
        this.intentStore = intentStore;
    }

    public Optional<IntentContract> findAccessible(String intentId, HttpServletRequest request) {
        Object userId = request.getAttribute("paypalUserId");
        if (userId instanceof String authenticatedUserId) {
            return intentStore.findByIdAndUserId(intentId, authenticatedUserId);
        }
        return intentStore.findById(intentId);
    }
}