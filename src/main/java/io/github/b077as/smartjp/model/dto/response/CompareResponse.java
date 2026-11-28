package io.github.b077as.smartjp.model.dto.response;

import org.jspecify.annotations.Nullable;

import io.github.b077as.smartjp.model.dto.summary.SyncSummary;

/** Both sides side by side. {@code result} is ok, not_connected, bad_key, unreachable or failed. {@code local} is given whenever this desktop is connected, {@code remote} only when the result is ok. */
public record CompareResponse(String result, @Nullable SyncSummary local, @Nullable SyncSummary remote) {
}
