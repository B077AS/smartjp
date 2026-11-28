package io.github.b077as.smartjp.model.dto.summary;

import org.jspecify.annotations.Nullable;

/** Whether this desktop app is connected to a server, and its address (a record because it crosses the Jolt bridge). */
public record SyncStatus(boolean connected, @Nullable String address) {
}
