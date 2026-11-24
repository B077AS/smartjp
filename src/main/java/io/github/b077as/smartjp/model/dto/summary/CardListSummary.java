package io.github.b077as.smartjp.model.dto.summary;

import java.util.List;

import io.github.b077as.smartjp.model.dto.NonNull;

/**
 * A saved flashcard list as the page sees it. A record, because it crosses the Jolt bridge and Jolt writes the TypeScript type
 * of records (the one exception to "classes with Lombok, never records"); {@code savedAt} is an ISO instant.
 */
public record CardListSummary(@NonNull String id, @NonNull String name, @NonNull List<String> cards, @NonNull String savedAt) {
}
