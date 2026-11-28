package io.github.b077as.smartjp.model.dto.request;

import org.jspecify.annotations.Nullable;

/** The server's address and its access key, as typed. {@code @Nullable} on purpose: a missing field must reach the service and be answered. */
public record ConnectRequest(@Nullable String address, @Nullable String key) {
}
