package io.github.b077as.smartjp.model.dto.request;

/** The accent colour to keep, by its key (for example "indigo"). Deliberately without @NonNull: a missing one must reach the service and be answered "bad_accent". */
public record SaveAppearanceRequest(String accent) {
}
