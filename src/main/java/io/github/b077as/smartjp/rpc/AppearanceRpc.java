package io.github.b077as.smartjp.rpc;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.github.b077as.jolt.JoltRpc;
import io.github.b077as.smartjp.model.dto.request.SaveAppearanceRequest;
import io.github.b077as.smartjp.model.dto.response.SaveAppearanceResponse;
import io.github.b077as.smartjp.model.dto.summary.AppearanceSummary;
import io.github.b077as.smartjp.service.AppearanceService;
import lombok.RequiredArgsConstructor;

/** Desktop app: the page's door onto how this installation looks, as {@code jolt.call("appearance.get")} and {@code "appearance.save"}. */
@Component
@Profile("desktop")
@JoltRpc("appearance")
@RequiredArgsConstructor
public class AppearanceRpc {

    private final AppearanceService appearanceService;

    @JoltRpc
    public AppearanceSummary get() {
        return appearanceService.get();
    }

    @JoltRpc
    public SaveAppearanceResponse save(SaveAppearanceRequest request) {
        return appearanceService.save(request);
    }
}
