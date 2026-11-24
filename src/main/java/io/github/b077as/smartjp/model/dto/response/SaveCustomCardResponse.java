package io.github.b077as.smartjp.model.dto.response;

import io.github.b077as.smartjp.model.dto.NonNull;
import io.github.b077as.smartjp.model.dto.summary.CustomCardSummary;

/**
 * What came of writing a card. {@code result} is one of created, updated, empty_front, empty_answer, too_long (the front or the
 * answer is over the limit: nothing is cut off silently), too_many, not_found (an id that is not a card of this database).
 * The card is there only for created and updated.
 */
public record SaveCustomCardResponse(@NonNull String result, CustomCardSummary card) {
}
