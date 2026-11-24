package io.github.b077as.smartjp.model.dto.request;

/** The server's address and its access key, as typed. Deliberately without @NonNull: a missing field must reach the service and be answered. */
public record ConnectRequest(String address, String key) {
}
