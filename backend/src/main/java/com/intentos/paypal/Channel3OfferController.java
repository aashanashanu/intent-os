package com.intentos.paypal;

import com.intentos.intent.IntentAccessService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/intents/{intentId}/offers")
public class Channel3OfferController {
    private final IntentAccessService intentAccessService;
    private final Channel3OfferService offerService;

    public Channel3OfferController(IntentAccessService intentAccessService, Channel3OfferService offerService) {
        this.intentAccessService = intentAccessService;
        this.offerService = offerService;
    }

    @GetMapping
    public List<Channel3Offer> search(@PathVariable String intentId, HttpServletRequest request) {
        var intent = intentAccessService.findAccessible(intentId, request)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Intent not found"));
        return offerService.search(intent);
    }
}