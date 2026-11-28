package io.github.b077as.smartjp.model.dto.response;

/** What came of logging an answer: recorded, duplicate (that id was already logged), bad_id, bad_card or bad_mode. */
public record RecordAnswerResponse(String result) {
}
