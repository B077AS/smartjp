package io.github.b077as.smartjp.rpc;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.github.b077as.jolt.Application;
import io.github.b077as.jolt.JoltRpc;
import io.github.b077as.smartjp.model.dto.summary.AppInfoSummary;
import io.github.b077as.smartjp.service.AppInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Desktop app: the About tab's facts ({@code jolt.call("app.info")}) and links that open in the user's own browser
 * ({@code "app.openLink"}). A window of its own has nowhere to show a page of another site, and a link in it would replace the app.
 * The website needs neither: a normal link opens in a new tab.
 */
@Slf4j
@Component
@Profile("desktop")
@JoltRpc("app")
@RequiredArgsConstructor
public class AppRpc {

    private final AppInfoService appInfoService;
    private final Application application;

    @JoltRpc
    public AppInfoSummary info() {
        return appInfoService.info();
    }

    /** Only http and https addresses; Jolt refuses anything else as well. Answers false when the system could not open it. */
    @JoltRpc
    public boolean openLink(String url) throws InterruptedException {
        URI uri;
        try {
            uri = new URI(url == null ? "" : url.trim());
        } catch (URISyntaxException e) {
            return false;
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))) {
            return false;
        }
        try {
            return application.openExternal(uri).get();
        } catch (ExecutionException e) {
            log.warn("A link could not be opened: {}", e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
            return false;
        }
    }
}
