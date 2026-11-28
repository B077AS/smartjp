package io.github.b077as.smartjp.model.dto.request;

import java.util.List;

import org.jspecify.annotations.Nullable;

/** Save these cards under this name. {@code @Nullable} on purpose: a missing name must reach the service and be answered "empty_name". */
public record SaveListRequest(@Nullable String name, @Nullable List<String> cards) {
}
