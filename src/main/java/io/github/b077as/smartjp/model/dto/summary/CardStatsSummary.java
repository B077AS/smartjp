package io.github.b077as.smartjp.model.dto.summary;

/** How often one card was answered and how often correctly (a record because it crosses the Jolt bridge); the misses are seen minus correct. */
public record CardStatsSummary(String cardId, long seen, long correct) {
}
