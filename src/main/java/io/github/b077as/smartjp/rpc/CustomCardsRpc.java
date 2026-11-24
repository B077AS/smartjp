package io.github.b077as.smartjp.rpc;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.github.b077as.jolt.JoltRpc;
import io.github.b077as.smartjp.model.dto.request.SaveCustomCardRequest;
import io.github.b077as.smartjp.model.dto.response.SaveCustomCardResponse;
import io.github.b077as.smartjp.model.dto.summary.CustomCardSummary;
import io.github.b077as.smartjp.service.CustomCardsService;
import lombok.RequiredArgsConstructor;

/** Desktop app: the page's door onto the cards the user wrote, as {@code jolt.call("customCards.all")}, {@code "customCards.save"} and {@code "customCards.delete"}. */
@Component
@Profile("desktop")
@JoltRpc("customCards")
@RequiredArgsConstructor
public class CustomCardsRpc {

    private final CustomCardsService customCardsService;

    @JoltRpc
    public List<CustomCardSummary> all() {
        return customCardsService.all();
    }

    @JoltRpc
    public SaveCustomCardResponse save(SaveCustomCardRequest request) {
        return customCardsService.save(request);
    }

    @JoltRpc
    public void delete(String id) {
        customCardsService.delete(id);
    }
}
