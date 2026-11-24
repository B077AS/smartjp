package io.github.b077as.smartjp.rpc;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.github.b077as.jolt.JoltRpc;
import io.github.b077as.smartjp.model.dto.request.RecordAnswerRequest;
import io.github.b077as.smartjp.model.dto.response.RecordAnswerResponse;
import io.github.b077as.smartjp.model.dto.summary.CardStatsSummary;
import io.github.b077as.smartjp.service.AnswersService;
import lombok.RequiredArgsConstructor;

/** Desktop app: the page's door onto the answer log, as {@code jolt.call("answers.stats")} and {@code "answers.record"}. */
@Component
@Profile("desktop")
@JoltRpc("answers")
@RequiredArgsConstructor
public class AnswersRpc {

    private final AnswersService answersService;

    @JoltRpc
    public List<CardStatsSummary> stats() {
        return answersService.stats();
    }

    @JoltRpc
    public RecordAnswerResponse record(RecordAnswerRequest request) {
        return answersService.record(request);
    }
}
