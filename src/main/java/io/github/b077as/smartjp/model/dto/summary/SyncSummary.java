package io.github.b077as.smartjp.model.dto.summary;

import org.jspecify.annotations.Nullable;

/** What one side holds, for the Sync screen (a record because it crosses the Jolt bridge). The times are ISO instants, or null when there is none yet. */
public record SyncSummary(long answers, @Nullable String lastAnswerAt, int lists, int customCards, @Nullable String lastSyncAt) {
}
