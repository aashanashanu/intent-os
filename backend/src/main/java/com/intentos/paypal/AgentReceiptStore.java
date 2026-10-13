package com.intentos.paypal;

import java.util.List;

public interface AgentReceiptStore {
    boolean saveIfAbsent(AgentReceipt receipt);

    List<AgentReceipt> findAllByIntentId(String intentId);
}