package io.github.b077as.smartjp.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * What the other side answered to one request, as a value (no exceptions for "the server is down"): {@code status} is the HTTP
 * status, or 0 when it could not be reached at all; {@code body} is the parsed JSON of a 200 answer, or null.
 */
@Getter
@AllArgsConstructor
public class RemoteReply<T> {

    private final int status;
    private final T body;

    public boolean ok() {
        return status == 200 && body != null;
    }
}
