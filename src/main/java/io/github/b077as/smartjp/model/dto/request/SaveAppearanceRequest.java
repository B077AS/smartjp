package io.github.b077as.smartjp.model.dto.request;

import org.jspecify.annotations.Nullable;

/** The accent colour to keep, by its key (for example "indigo"). {@code @Nullable} on purpose: a missing one must reach the service and be answered "bad_accent". */
public record SaveAppearanceRequest(@Nullable String accent) {
}
