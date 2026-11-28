package io.github.b077as.smartjp.model.dto.response;

import org.jspecify.annotations.Nullable;

import io.github.b077as.smartjp.model.dto.summary.CardListSummary;

/**
 * What came of a save. {@code result} is one of created, replaced (a list of that name existed), empty_name, no_cards,
 * bad_cards, too_many. The list is there only for created and replaced.
 */
public record SaveListResponse(String result, @Nullable CardListSummary list) {
}
