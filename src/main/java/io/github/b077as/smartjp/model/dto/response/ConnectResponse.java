package io.github.b077as.smartjp.model.dto.response;

import io.github.b077as.smartjp.model.dto.NonNull;
import io.github.b077as.smartjp.model.dto.summary.SyncStatus;

/** What came of connecting: connected, bad_address, bad_key, unreachable, no_keystore or failed. */
public record ConnectResponse(@NonNull String result, @NonNull SyncStatus status) {
}
