package io.github.b077as.smartjp.model.dto.summary;

import io.github.b077as.smartjp.model.dto.NonNull;

/** A card the user wrote, as the page sees it (a record because it crosses the Jolt bridge); {@code savedAt} is an ISO instant. */
public record CustomCardSummary(@NonNull String id, @NonNull String front, @NonNull String answer, @NonNull String savedAt) {
}
