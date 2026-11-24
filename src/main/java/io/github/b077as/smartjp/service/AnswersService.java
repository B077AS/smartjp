package io.github.b077as.smartjp.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.b077as.smartjp.model.db.AnswerEvent;
import io.github.b077as.smartjp.model.dto.request.RecordAnswerRequest;
import io.github.b077as.smartjp.model.dto.response.RecordAnswerResponse;
import io.github.b077as.smartjp.model.dto.summary.CardStatsSummary;
import io.github.b077as.smartjp.repository.AnswerEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The log of answers (see AnswerEvent) and what is worked out from it. The page asks for the totals per card once at start and
 * keeps them up to date itself, so the whole log is never sent to it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnswersService {

    /** Ids are the page's (a UUID) or a card's name, never longer than this; the same limit as ListsService.MAX_CARD_ID. */
    public static final int MAX_ID = 64;
    /** A card left open for longer than this counts as this long: the time is only a hint, not worth refusing an answer over. */
    public static final long MAX_MS = 60L * 60 * 1000;
    private static final Set<String> MODES = Set.of("reading", "kana", "meaning", "flip");

    private final AnswerEventRepository repository;
    private final Clock clock;

    @Transactional
    public RecordAnswerResponse record(RecordAnswerRequest request) {
        if (!validId(request.id())) {
            return new RecordAnswerResponse("bad_id");
        }
        if (!validId(request.cardId())) {
            return new RecordAnswerResponse("bad_card");
        }
        if (!validMode(request.mode())) {
            return new RecordAnswerResponse("bad_mode");
        }
        if (repository.existsById(request.id())) {
            return new RecordAnswerResponse("duplicate");
        }
        repository.save(AnswerEvent.builder()
                .eventId(request.id())
                .cardId(request.cardId())
                .correct(request.correct())
                .ms(clampMs(request.ms()))
                .mode(request.mode())
                .answeredAt(Instant.now(clock))
                .build());
        return new RecordAnswerResponse("recorded");
    }

    @Transactional(readOnly = true)
    public List<CardStatsSummary> stats() {
        return repository.statsByCard().stream().map(row -> new CardStatsSummary(row.getCardId(), row.getSeen(), row.getCorrect())).toList();
    }

    // The rules for one event, shared with the sync (SyncStore), which stores events that arrive from the other side.
    static boolean validId(String id) {
        return id != null && !id.isBlank() && id.length() <= MAX_ID;
    }

    static boolean validMode(String mode) {
        return mode != null && MODES.contains(mode);
    }

    static long clampMs(long ms) {
        return Math.max(0, Math.min(ms, MAX_MS));
    }
}
