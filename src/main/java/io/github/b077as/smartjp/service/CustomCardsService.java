package io.github.b077as.smartjp.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.b077as.smartjp.model.db.CustomCard;
import io.github.b077as.smartjp.model.db.Deletion;
import io.github.b077as.smartjp.model.dto.request.SaveCustomCardRequest;
import io.github.b077as.smartjp.model.dto.response.SaveCustomCardResponse;
import io.github.b077as.smartjp.model.dto.summary.CustomCardSummary;
import io.github.b077as.smartjp.repository.CustomCardRepository;
import io.github.b077as.smartjp.repository.DeletionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The cards the user writes themselves, in the SQLite database. Like ListsService it holds the rules once, and the website's
 * controller and the desktop app's @JoltRpc bean are thin doors onto it. A card is any text on the front and any text as the
 * answer (words, phrases, a sentence), so the limits are about a paragraph, not a character.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomCardsService {

    /** Characters (not bytes) of the front and of the answer. */
    public static final int MAX_TEXT = 500;
    public static final int MAX_CARDS = 2000;

    private final CustomCardRepository repository;
    private final DeletionRepository deletions;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<CustomCardSummary> all() {
        return repository.findAllByOrderByCreatedAtAscCardIdAsc().stream().map(CustomCardsService::toSummary).toList();
    }

    @Transactional
    public SaveCustomCardResponse save(SaveCustomCardRequest request) {
        String front = clean(request.front());
        String answer = clean(request.answer());
        if (front.isEmpty()) {
            return refused("empty_front");
        }
        if (answer.isEmpty()) {
            return refused("empty_answer");
        }
        if (length(front) > MAX_TEXT || length(answer) > MAX_TEXT) {
            return refused("too_long");
        }

        Instant now = Instant.now(clock);
        if (request.id() != null && !request.id().isBlank()) {
            Optional<CustomCard> existing = find(request.id());
            if (existing.isEmpty()) {
                return refused("not_found");
            }
            CustomCard card = existing.get();
            card.setFront(front);
            card.setAnswer(answer);
            card.setSavedAt(now);
            return new SaveCustomCardResponse("updated", toSummary(repository.save(card)));
        }

        if (repository.count() >= MAX_CARDS) {
            return refused("too_many");
        }
        CustomCard created = repository.save(CustomCard.builder().cardId(UUID.randomUUID()).front(front).answer(answer).createdAt(now).savedAt(now).build());
        log.debug("Custom card created");
        return new SaveCustomCardResponse("created", toSummary(created));
    }

    /** Deleting a card that is not there is not an error: the page wanted it gone and it is. A note of the deletion is kept for the sync. */
    @Transactional
    public void delete(String id) {
        find(id).ifPresent(card -> {
            repository.delete(card);
            deletions.save(Deletion.of(Deletion.CARD, card.getCardId().toString(), Instant.now(clock)));
        });
    }

    private Optional<CustomCard> find(String id) {
        try {
            return repository.findById(UUID.fromString(id));
        } catch (IllegalArgumentException | NullPointerException e) {
            return Optional.empty(); // not an id this database ever gave out
        }
    }

    private static CustomCardSummary toSummary(CustomCard card) {
        return new CustomCardSummary(card.getCardId().toString(), card.getFront(), card.getAnswer(), card.getSavedAt().toString());
    }

    private static SaveCustomCardResponse refused(String result) {
        return new SaveCustomCardResponse(result, null);
    }

    /** Line breaks become \n and the ends are trimmed; what is inside (including line breaks) is kept as written. */
    static String clean(String text) {
        return text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n').strip();
    }

    private static int length(String text) {
        return text.codePointCount(0, text.length());
    }
}
