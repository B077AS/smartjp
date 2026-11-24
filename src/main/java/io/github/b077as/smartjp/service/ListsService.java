package io.github.b077as.smartjp.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.b077as.smartjp.model.db.Deletion;
import io.github.b077as.smartjp.model.db.SavedList;
import io.github.b077as.smartjp.model.dto.request.SaveListRequest;
import io.github.b077as.smartjp.model.dto.response.SaveListResponse;
import io.github.b077as.smartjp.model.dto.summary.CardListSummary;
import io.github.b077as.smartjp.repository.DeletionRepository;
import io.github.b077as.smartjp.repository.SavedListRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The user's flashcard lists, in the SQLite database. The website's controller and the desktop app's @JoltRpc bean are both
 * thin doors onto this class, so the rules (names, limits, "same name replaces") exist once.
 * The page keeps only what it needs to show: it never decides whether a save is allowed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ListsService {

    public static final int MAX_NAME = 40;
    public static final int MAX_LISTS = 50;
    /** The kana and the 2136 jouyou kanji are 2344 cards; no real list is longer. */
    public static final int MAX_CARDS = 5000;
    /** "custom:" plus a UUID is 43 characters. */
    public static final int MAX_CARD_ID = 64;

    private final SavedListRepository repository;
    private final DeletionRepository deletions;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<CardListSummary> all() {
        return repository.findAllByOrderByCreatedAtAscListIdAsc().stream().map(ListsService::toSummary).toList();
    }

    /** Saves the cards under a name. A list with the same name (ignoring case) is replaced and keeps its place. */
    @Transactional
    public SaveListResponse save(SaveListRequest request) {
        String name = cleanName(request.name());
        if (name.isEmpty()) {
            return refused("empty_name");
        }
        if (request.cards() == null || request.cards().isEmpty()) {
            return refused("no_cards");
        }
        List<String> cards = request.cards().stream().distinct().toList();
        if (cards.size() > MAX_CARDS || cards.stream().anyMatch(id -> id == null || id.isBlank() || id.length() > MAX_CARD_ID)) {
            return refused("bad_cards");
        }

        String key = name.toLowerCase(Locale.ROOT);
        Instant now = Instant.now(clock);
        deletions.deleteById(Deletion.idOf(Deletion.LIST, key)); // saved again: it is no longer a deleted list
        Optional<SavedList> existing = repository.findByNameKey(key);
        if (existing.isPresent()) {
            SavedList list = existing.get();
            list.setName(name);
            list.setCards(cards);
            list.setSavedAt(now);
            return new SaveListResponse("replaced", toSummary(repository.save(list)));
        }

        if (repository.count() >= MAX_LISTS) {
            return refused("too_many");
        }
        SavedList created = repository.save(SavedList.builder().name(name).nameKey(key).cards(cards).createdAt(now).savedAt(now).build());
        log.debug("List saved: {} cards", cards.size());
        return new SaveListResponse("created", toSummary(created));
    }

    /** Deleting a list that is not there is not an error: the page wanted it gone and it is. */
    @Transactional
    public void delete(String id) {
        UUID listId;
        try {
            listId = UUID.fromString(id);
        } catch (IllegalArgumentException | NullPointerException e) {
            return; // not an id this database ever gave out, so there is nothing to delete
        }
        repository.findById(listId).ifPresent(list -> {
            repository.delete(list);
            deletions.save(Deletion.of(Deletion.LIST, list.getNameKey(), Instant.now(clock))); // so a sync does not bring it back
        });
    }

    private static CardListSummary toSummary(SavedList list) {
        return new CardListSummary(list.getListId().toString(), list.getName(), list.getCards(), list.getSavedAt().toString());
    }

    private static SaveListResponse refused(String result) {
        return new SaveListResponse(result, null);
    }

    /** Whitespace runs become one space, the ends are trimmed, and a name is cut at MAX_NAME characters. */
    static String cleanName(String name) {
        if (name == null) {
            return "";
        }
        String clean = name.replaceAll("\\s+", " ").strip();
        return clean.codePointCount(0, clean.length()) <= MAX_NAME ? clean : clean.substring(0, clean.offsetByCodePoints(0, MAX_NAME)).strip();
    }
}
