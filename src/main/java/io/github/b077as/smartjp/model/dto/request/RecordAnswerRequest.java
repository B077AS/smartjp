package io.github.b077as.smartjp.model.dto.request;

/**
 * One answer to log. {@code id} is made by the page (unique per answer; sending the same one again changes nothing).
 * Deliberately without @NonNull on the texts: a missing one must reach the service and be answered "bad_id" / "bad_card" / "bad_mode".
 */
public record RecordAnswerRequest(String id, String cardId, boolean correct, long ms, String mode) {
}
