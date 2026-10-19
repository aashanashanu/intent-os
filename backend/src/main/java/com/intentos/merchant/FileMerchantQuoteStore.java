package com.intentos.merchant;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnProperty(name = "intentos.elastic.enabled", havingValue = "false", matchIfMissing = true)
public class FileMerchantQuoteStore implements MerchantQuoteStore {
    private final ObjectMapper objectMapper;
    private final Path directory;

    @Autowired
    public FileMerchantQuoteStore(ObjectMapper objectMapper,
            @Value("${intentos.merchant.quote-directory:.intentos/merchant-quotes}") String directory) {
        this(objectMapper, Path.of(directory));
    }

    FileMerchantQuoteStore(ObjectMapper objectMapper, Path directory) {
        this.objectMapper = objectMapper;
        this.directory = directory;
    }

    @Override
    public MerchantQuote save(MerchantQuote quote) {
        try {
            Files.createDirectories(directory);
            Files.write(directory.resolve(quote.quoteId() + ".json"), objectMapper.writeValueAsBytes(quote),
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return quote;
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Merchant quote could not be persisted", exception);
        }
    }

    @Override
    public Optional<MerchantQuote> find(String quoteId) {
        Path quotePath = directory.resolve(quoteId + ".json");
        if (!Files.exists(quotePath)) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(Files.readAllBytes(quotePath), MerchantQuote.class));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Stored merchant quote could not be read", exception);
        }
    }
}