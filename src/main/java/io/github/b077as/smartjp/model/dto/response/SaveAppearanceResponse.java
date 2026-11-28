package io.github.b077as.smartjp.model.dto.response;

import org.jspecify.annotations.Nullable;

/** What came of keeping the accent: saved or bad_accent. {@code accent} is the one now kept (null when none has ever been kept). */
public record SaveAppearanceResponse(String result, @Nullable String accent) {
}
