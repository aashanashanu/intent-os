package com.intentos.paypal;

import java.util.List;

public interface AgentReceiptStore {
    boolean saveIfAbsent(AgentReceipt receipt);

    List<AgentReceipt> findAllByIntentId(String intentId);

    List<AgentReceipt> search(String intentId, String query, String status, int limit);

    default String searchMode() {
        return "LEXICAL";
    }
}