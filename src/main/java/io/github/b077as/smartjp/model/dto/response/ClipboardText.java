package io.github.b077as.smartjp.model.dto.response;

import io.github.b077as.smartjp.model.dto.NonNull;

/** The text on the system clipboard ("" when there is none). A record because it crosses the Jolt bridge. */
public record ClipboardText(@NonNull String text) {
}
