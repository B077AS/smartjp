package io.github.b077as.smartjp.model.dto.summary;

import java.util.List;

/**
 * A saved flashcard list as the page sees it. A record, because it crosses the Jolt bridge and Jolt writes the TypeScript type
 * of records (the one exception to "classes with Lombok, never records"); {@code savedAt} is an ISO instant.
 */
public record CardListSummary(String id, String name, List<String> cards, String savedAt) {
}
