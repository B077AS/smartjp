package io.github.b077as.smartjp.model.dto.response;

import org.jspecify.annotations.Nullable;

import io.github.b077as.smartjp.model.dto.summary.SyncCounts;

/**
 * What came of a sync: done, bad_action, not_connected, bad_key, unreachable or failed. {@code backup} is the file the overwritten side
 * saved first and {@code backupSide} says which side it is ("desktop" or "server"); both are null for a merge. {@code sent} is what the
 * server took in, {@code received} what this desktop took in.
 */
public record RunSyncResponse(String result, @Nullable String backup, @Nullable String backupSide, @Nullable SyncCounts sent, @Nullable SyncCounts received) {
}
