package io.github.b077as.smartjp.model.dto.request;

import org.jspecify.annotations.Nullable;

/**
 * Write a card. Without an {@code id} a new card is made; with the id of an existing card that card is changed.
 * Everything is {@code @Nullable} on purpose: a missing front or answer must reach the service and be answered "empty_front" / "empty_answer".
 */
public record SaveCustomCardRequest(@Nullable String id, @Nullable String front, @Nullable String answer) {
}
