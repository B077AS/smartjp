package io.github.b077as.smartjp.model.dto.request;

/** What to do: "merge", "pull" (replace this desktop with the server) or "push" (replace the server with this desktop). */
public record RunSyncRequest(String action) {
}
