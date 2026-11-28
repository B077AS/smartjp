package io.github.b077as.smartjp.model.dto.request;

import org.jspecify.annotations.Nullable;

/** What to do: "merge", "pull" (replace this desktop with the server) or "push" (replace the server with this desktop). */
public record RunSyncRequest(@Nullable String action) {
}
