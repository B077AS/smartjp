package io.github.b077as.smartjp.model.dto.request;

import org.jspecify.annotations.Nullable;

/**
 * One answer to log. {@code id} is made by the page (unique per answer; sending the same one again changes nothing).
 * The texts are {@code @Nullable} on purpose: a missing one must reach the service and be answered "bad_id" / "bad_card" / "bad_mode".
 */
public record RecordAnswerRequest(@Nullable String id, @Nullable String cardId, boolean correct, long ms, @Nullable String mode) {
}
