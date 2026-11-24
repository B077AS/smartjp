package io.github.b077as.smartjp.model.dto.summary;

/** Whether this desktop app is connected to a server, and its address (a record because it crosses the Jolt bridge). */
public record SyncStatus(boolean connected, String address) {
}
