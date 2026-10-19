package com.intentos.merchant;

import java.util.Optional;

public interface MerchantQuoteStore {
    MerchantQuote save(MerchantQuote quote);

    Optional<MerchantQuote> find(String quoteId);
}