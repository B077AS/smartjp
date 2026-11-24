package io.github.b077as.smartjp.service;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.b077as.smartjp.model.db.AnswerEvent;
import io.github.b077as.smartjp.model.db.CustomCard;
import io.github.b077as.smartjp.model.db.Deletion;
import io.github.b077as.smartjp.model.db.SavedList;
import io.github.b077as.smartjp.model.dto.summary.SyncSummary;
import io.github.b077as.smartjp.model.dto.sync.SyncAnswer;
import io.github.b077as.smartjp.model.dto.sync.SyncCard;
import io.github.b077as.smartjp.model.dto.sync.SyncDeletion;
import io.github.b077as.smartjp.model.dto.sync.SyncList;
import io.github.b077as.smartjp.model.dto.sync.SyncOutcome;
import io.github.b077as.smartjp.model.dto.sync.SyncSnapshot;
import io.github.b077as.smartjp.repository.AnswerEventRepository;
import io.github.b077as.smartjp.repository.CustomCardRepository;
import io.github.b077as.smartjp.repository.DeletionRepository;
import io.github.b077as.smartjp.repository.SavedListRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The database side of a sync: what this side holds ({@link #summary}, {@link #snapshot}) and how it takes in what the other side
 * holds ({@link #merge}, {@link #replaceAll}). The two sides are peers running this same code, each with its own SQLite file.
 * <p>
 * The merge rules (they make merging the same data twice, or in either order, give the same result):
 * <ul>
 *   <li><b>Answers</b> are an append-only log: an event this side does not have is added, one it has (same id) is ignored.</li>
 *   <li><b>Lists</b> (known by their lower-case name) and <b>custom cards</b> (known by their id): the version with the newer
 *       {@code saved_at} wins. A deletion is remembered ({@link Deletion}) and removes the item here if the item is not newer
 *       than the deletion; saving it again afterwards (a newer version) brings it back.</li>
 * </ul>
 * "Newer" compares the two devices' clocks, so a clock that is badly wrong makes the wrong version win; there is no better
 * answer without a server that decides the time.
 * <p>
 * A merge only ever adds or changes; a replace first empties this side. Whatever arrives is checked with the same rules as a
 * save through the page (lengths, the answer modes), and what does not fit is skipped, not stored.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncStore {

    public static final String LAST_SYNC = "sync.last";
    /** A sanity limit on what one request may carry, not a promise about what a person will ever have. */
    public static final int MAX_ANSWERS = 1_000_000;
    public static final int MAX_LISTS = 1_000;
    public static final int MAX_CARDS = 10_000;
    private static final int FLUSH_EVERY = 1_000;

    private final SavedListRepository lists;
    private final CustomCardRepository cards;
    private final AnswerEventRepository answers;
    private final DeletionRepository deletions;
    private final AppSettingsService settings;
    private final Clock clock;

    @PersistenceContext
    private EntityManager em;

    @Transactional(readOnly = true)
    public SyncSummary summary() {
        Instant lastAnswer = answers.lastAnsweredAt();
        return new SyncSummary(answers.count(), lastAnswer == null ? null : lastAnswer.toString(), (int) lists.count(), (int) cards.count(),
                settings.get(LAST_SYNC).orElse(null));
    }

    @Transactional(readOnly = true)
    public SyncSnapshot snapshot() {
        return SyncSnapshot.builder()
                .lists(lists.findAllByOrderByCreatedAtAscListIdAsc().stream()
                        .map(l -> SyncList.builder().name(l.getName()).cards(List.copyOf(l.getCards())).createdAt(l.getCreatedAt().toString()).savedAt(l.getSavedAt().toString()).build())
                        .toList())
                .customCards(cards.findAllByOrderByCreatedAtAscCardIdAsc().stream()
                        .map(c -> SyncCard.builder().id(c.getCardId().toString()).front(c.getFront()).answer(c.getAnswer()).createdAt(c.getCreatedAt().toString()).savedAt(c.getSavedAt().toString()).build())
                        .toList())
                .answers(answers.findAll().stream()
                        .map(a -> SyncAnswer.builder().id(a.getEventId()).cardId(a.getCardId()).correct(a.isCorrect()).ms(a.getMs()).mode(a.getMode()).answeredAt(a.getAnsweredAt().toString()).build())
                        .toList())
                .deletions(deletions.findAll().stream()
                        .map(d -> SyncDeletion.builder().kind(d.getKind()).key(d.getItemKey()).deletedAt(d.getDeletedAt().toString()).build())
                        .toList())
                .build();
    }

    /** Adds and updates, never loses anything that is newer here. Throws IllegalArgumentException for a snapshot over the limits. */
    @Transactional
    public SyncOutcome merge(SyncSnapshot in) {
        checkSize(in);
        return apply(in);
    }

    /** Empties this side and takes the snapshot in. The caller saves a backup first (SyncService). */
    @Transactional
    public SyncOutcome replaceAll(SyncSnapshot in) {
        checkSize(in);
        answers.deleteAllInBatch();
        lists.deleteAllInBatch();
        cards.deleteAllInBatch();
        deletions.deleteAllInBatch();
        em.clear();
        return apply(in);
    }

    /** Remembers that this side took part in a sync just now. */
    public void markSynced() {
        settings.put(LAST_SYNC, Instant.now(clock).toString());
    }

    private SyncOutcome apply(SyncSnapshot in) {
        SyncOutcome out = new SyncOutcome();
        Map<String, Deletion> notes = new HashMap<>();
        deletions.findAll().forEach(d -> notes.put(d.getId(), d));

        for (SyncDeletion deletion : orEmpty(in.getDeletions())) {
            applyDeletion(deletion, notes, out);
        }
        for (SyncList list : orEmpty(in.getLists())) {
            applyList(list, notes, out);
        }
        for (SyncCard card : orEmpty(in.getCustomCards())) {
            applyCard(card, notes, out);
        }
        applyAnswers(orEmpty(in.getAnswers()), out);
        log.info("Took in a sync: {} answers, {} lists, {} cards added or changed, {} removed", out.getAnswers(), out.getLists(), out.getCards(), out.getRemoved());
        return out;
    }

    private void applyDeletion(SyncDeletion d, Map<String, Deletion> notes, SyncOutcome out) {
        Instant at = parse(d.getDeletedAt());
        String kind = d.getKind();
        String key = d.getKey();
        boolean isList = Deletion.LIST.equals(kind);
        boolean isCard = Deletion.CARD.equals(kind);
        if (at == null || key == null || key.isBlank() || key.length() > 160 || (!isList && !isCard)) {
            return;
        }
        if (isCard) {
            UUID id = uuid(key);
            if (id == null) {
                return;
            }
            key = id.toString();
            Optional<CustomCard> found = cards.findById(id);
            if (found.isPresent()) {
                if (found.get().getSavedAt().isAfter(at)) {
                    return; // edited after it was deleted on the other side: that version stays
                }
                cards.delete(found.get());
                em.flush(); // deletes run after inserts otherwise
                out.setRemoved(out.getRemoved() + 1);
            }
        } else {
            Optional<SavedList> found = lists.findByNameKey(key);
            if (found.isPresent()) {
                if (found.get().getSavedAt().isAfter(at)) {
                    return;
                }
                lists.delete(found.get());
                em.flush();
                out.setRemoved(out.getRemoved() + 1);
            }
        }
        Deletion existing = notes.get(Deletion.idOf(kind, key));
        if (existing == null || existing.getDeletedAt().isBefore(at)) {
            Deletion note = deletions.save(Deletion.of(kind, key, at));
            notes.put(note.getId(), note);
        }
    }

    private void applyList(SyncList incoming, Map<String, Deletion> notes, SyncOutcome out) {
        String name = ListsService.cleanName(incoming.getName());
        List<String> ids = incoming.getCards() == null ? null : incoming.getCards().stream().distinct().toList();
        Instant saved = parse(incoming.getSavedAt());
        if (name.isEmpty() || saved == null || ids == null || ids.isEmpty() || ids.size() > ListsService.MAX_CARDS
                || ids.stream().anyMatch(id -> id == null || id.isBlank() || id.length() > ListsService.MAX_CARD_ID)) {
            return;
        }
        Instant created = Optional.ofNullable(parse(incoming.getCreatedAt())).orElse(saved);
        String key = name.toLowerCase(Locale.ROOT);

        Deletion note = notes.get(Deletion.idOf(Deletion.LIST, key));
        if (note != null && !note.getDeletedAt().isBefore(saved)) {
            return; // deleted at or after this version
        }
        Optional<SavedList> local = lists.findByNameKey(key);
        if (local.isEmpty()) {
            em.persist(SavedList.builder().name(name).nameKey(key).cards(ids).createdAt(created).savedAt(saved).build());
            out.setLists(out.getLists() + 1);
        } else if (saved.isAfter(local.get().getSavedAt())) {
            SavedList list = local.get();
            list.setName(name);
            list.setCards(ids);
            list.setSavedAt(saved);
            out.setLists(out.getLists() + 1);
        }
        if (note != null) {
            deletions.delete(note); // the list is back
            notes.remove(note.getId());
        }
    }

    private void applyCard(SyncCard incoming, Map<String, Deletion> notes, SyncOutcome out) {
        UUID id = uuid(incoming.getId());
        String front = CustomCardsService.clean(incoming.getFront());
        String answer = CustomCardsService.clean(incoming.getAnswer());
        Instant saved = parse(incoming.getSavedAt());
        if (id == null || saved == null || front.isEmpty() || answer.isEmpty()
                || front.codePointCount(0, front.length()) > CustomCardsService.MAX_TEXT
                || answer.codePointCount(0, answer.length()) > CustomCardsService.MAX_TEXT) {
            return;
        }
        Instant created = Optional.ofNullable(parse(incoming.getCreatedAt())).orElse(saved);

        Deletion note = notes.get(Deletion.idOf(Deletion.CARD, id.toString()));
        if (note != null && !note.getDeletedAt().isBefore(saved)) {
            return;
        }
        Optional<CustomCard> local = cards.findById(id);
        if (local.isEmpty()) {
            em.persist(CustomCard.builder().cardId(id).front(front).answer(answer).createdAt(created).savedAt(saved).build());
            out.setCards(out.getCards() + 1);
        } else if (saved.isAfter(local.get().getSavedAt())) {
            CustomCard card = local.get();
            card.setFront(front);
            card.setAnswer(answer);
            card.setSavedAt(saved);
            out.setCards(out.getCards() + 1);
        }
        if (note != null) {
            deletions.delete(note);
            notes.remove(note.getId());
        }
    }

    private void applyAnswers(List<SyncAnswer> incoming, SyncOutcome out) {
        Set<String> known = new HashSet<>(answers.allIds());
        int pending = 0;
        for (SyncAnswer a : incoming) {
            Instant at = parse(a.getAnsweredAt());
            if (at == null || !AnswersService.validId(a.getId()) || !AnswersService.validId(a.getCardId()) || !AnswersService.validMode(a.getMode())) {
                continue;
            }
            if (!known.add(a.getId())) {
                continue; // this side has that answer already
            }
            em.persist(AnswerEvent.builder().eventId(a.getId()).cardId(a.getCardId()).correct(a.isCorrect()).ms(AnswersService.clampMs(a.getMs())).mode(a.getMode()).answeredAt(at).build());
            out.setAnswers(out.getAnswers() + 1);
            if (++pending % FLUSH_EVERY == 0) {
                em.flush();
                em.clear(); // thousands of events must not pile up in memory
            }
        }
    }

    private static void checkSize(SyncSnapshot in) {
        if (orEmpty(in.getAnswers()).size() > MAX_ANSWERS || orEmpty(in.getLists()).size() > MAX_LISTS || orEmpty(in.getCustomCards()).size() > MAX_CARDS) {
            throw new IllegalArgumentException("The snapshot is larger than this application accepts.");
        }
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static Instant parse(String text) {
        try {
            return text == null ? null : Instant.parse(text);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static UUID uuid(String text) {
        try {
            return text == null ? null : UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
