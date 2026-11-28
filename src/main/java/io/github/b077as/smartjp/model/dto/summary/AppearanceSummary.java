package io.github.b077as.smartjp.model.dto.summary;

import org.jspecify.annotations.Nullable;

/** How this installation looks: the accent colour the user chose, or null while they never chose one (the page then uses its default). A record because it crosses the Jolt bridge. */
public record AppearanceSummary(@Nullable String accent) {
}
