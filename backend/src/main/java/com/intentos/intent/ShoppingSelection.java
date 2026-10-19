package com.intentos.intent;

import java.util.List;

public record ShoppingSelection(List<SelectedCandidate> items, String rationale, String modelId) {
    public ShoppingSelection(List<SelectedCandidate> items, String rationale) {
        this(items, rationale, null);
    }

    public ShoppingSelection {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public record SelectedCandidate(String candidateId, int quantity) {}
}