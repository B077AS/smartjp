package io.github.b077as.smartjp.service;

import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import io.github.b077as.smartjp.model.dto.request.SaveAppearanceRequest;
import io.github.b077as.smartjp.model.dto.response.SaveAppearanceResponse;
import io.github.b077as.smartjp.model.dto.summary.AppearanceSummary;
import lombok.RequiredArgsConstructor;

/**
 * How this installation looks (for now the accent colour), kept in app_settings. It is a taste of the device and NOT part of a sync:
 * the snapshot has no such part, and a replace only empties lists, cards, answers and deletions, never the settings. So a desktop
 * and its server can have different colours.
 */
@Service
@RequiredArgsConstructor
public class AppearanceService {

    static final String ACCENT = "appearance.accent";
    /** Only a key shape is checked here; the list of colours belongs to the page, which falls back to its default for a key it does not know. */
    private static final Pattern KEY = Pattern.compile("[a-z0-9-]{1,32}");

    private final AppSettingsService settings;

    public AppearanceSummary get() {
        return new AppearanceSummary(settings.get(ACCENT).orElse(null));
    }

    public SaveAppearanceResponse save(SaveAppearanceRequest request) {
        String accent = request.accent();
        if (accent == null || !KEY.matcher(accent).matches()) {
            return new SaveAppearanceResponse("bad_accent", settings.get(ACCENT).orElse(null));
        }
        settings.put(ACCENT, accent);
        return new SaveAppearanceResponse("saved", accent);
    }
}
