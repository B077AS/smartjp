package io.github.b077as.smartjp.model.dto.request;

/**
 * Write a card. Without an {@code id} a new card is made; with the id of an existing card that card is changed.
 * Deliberately without @NonNull: a missing front or answer must reach the service and be answered "empty_front" / "empty_answer".
 */
public record SaveCustomCardRequest(String id, String front, String answer) {
}
