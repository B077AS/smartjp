package io.github.b077as.smartjp.model.dto.summary;

/** How many answers, lists and custom cards moved in one direction, and how many lists and cards were removed (a record because it crosses the Jolt bridge). */
public record SyncCounts(long answers, int lists, int cards, int removed) {
}
