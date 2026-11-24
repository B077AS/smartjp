package io.github.b077as.smartjp.model.dto.response;

import io.github.b077as.smartjp.model.dto.NonNull;

/** What came of logging an answer: recorded, duplicate (that id was already logged), bad_id, bad_card or bad_mode. */
public record RecordAnswerResponse(@NonNull String result) {
}
