package io.github.b077as.smartjp.model.dto.summary;

import io.github.b077as.smartjp.model.dto.NonNull;

/** What the About tab shows: the version of this build ("dev" for a run from the IDE), the operating system, the Java version and how it runs ("desktop" or "website"). A record because it crosses the Jolt bridge. */
public record AppInfoSummary(@NonNull String version, @NonNull String os, @NonNull String java, @NonNull String mode) {
}
