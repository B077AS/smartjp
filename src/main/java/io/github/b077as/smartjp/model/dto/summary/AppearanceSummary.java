package io.github.b077as.smartjp.model.dto.summary;

/** How this installation looks: the accent colour the user chose, or null while they never chose one (the page then uses its default). A record because it crosses the Jolt bridge. */
public record AppearanceSummary(String accent) {
}
