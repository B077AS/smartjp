package io.github.b077as.smartjp.model.dto.summary;

/** What the About tab shows: the version of this build ("dev" for a run from the IDE), the operating system, the Java version and how it runs ("desktop" or "website"). A record because it crosses the Jolt bridge. */
public record AppInfoSummary(String version, String os, String java, String mode) {
}
