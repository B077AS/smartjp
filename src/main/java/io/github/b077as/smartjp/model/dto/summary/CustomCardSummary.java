package io.github.b077as.smartjp.model.dto.summary;

/** A card the user wrote, as the page sees it (a record because it crosses the Jolt bridge); {@code savedAt} is an ISO instant. */
public record CustomCardSummary(String id, String front, String answer, String savedAt) {
}
