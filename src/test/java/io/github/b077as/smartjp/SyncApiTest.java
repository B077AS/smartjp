package io.github.b077as.smartjp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.google.gson.Gson;

import io.github.b077as.smartjp.model.db.Deletion;
import io.github.b077as.smartjp.model.dto.request.RecordAnswerRequest;
import io.github.b077as.smartjp.model.dto.request.SaveCustomCardRequest;
import io.github.b077as.smartjp.model.dto.request.SaveListRequest;
import io.github.b077as.smartjp.model.dto.summary.CardListSummary;
import io.github.b077as.smartjp.model.dto.summary.SyncSummary;
import io.github.b077as.smartjp.model.dto.sync.SyncAnswer;
import io.github.b077as.smartjp.model.dto.sync.SyncCard;
import io.github.b077as.smartjp.model.dto.sync.SyncDeletion;
import io.github.b077as.smartjp.model.dto.sync.SyncList;
import io.github.b077as.smartjp.model.dto.sync.SyncOutcome;
import io.github.b077as.smartjp.model.dto.sync.SyncSnapshot;
import io.github.b077as.smartjp.repository.DeletionRepository;
import io.github.b077as.smartjp.service.AnswersService;
import io.github.b077as.smartjp.service.AppSettingsService;
import io.github.b077as.smartjp.service.CustomCardsService;
import io.github.b077as.smartjp.service.ListsService;
import io.github.b077as.smartjp.service.SyncStore;

/** The server's side of a sync: summary, snapshot, merge and replace, on a real SQLite file in a temporary folder. */
@SpringBootTest
@AutoConfigureMockMvc
class SyncApiTest {

    private static final Path DATA = tempFolder();

    private static Path tempFolder() {
        try {
            return Files.createTempDirectory("smartjp-sync-test-");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void dataFolder(DynamicPropertyRegistry registry) {
        registry.add("smartjp.data-dir", DATA::toString);
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private Gson gson;
    @Autowired
    private ListsService lists;
    @Autowired
    private CustomCardsService cards;
    @Autowired
    private AnswersService answers;
    @Autowired
    private SyncStore store;
    @Autowired
    private DeletionRepository deletions;
    @Autowired
    private AppSettingsService settings;

    @BeforeEach
    void emptyTheDatabase() {
        store.replaceAll(empty());
        settings.remove(SyncStore.LAST_SYNC);
    }

    private static SyncSnapshot empty() {
        return new SyncSnapshot(List.of(), List.of(), List.of(), List.of());
    }

    private static SyncSnapshot snapshot(List<SyncList> lists, List<SyncCard> cards, List<SyncAnswer> answers, List<SyncDeletion> deletions) {
        return new SyncSnapshot(lists, cards, answers, deletions);
    }

    private static Instant hoursFromNow(long hours) {
        return Instant.now().plus(hours, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
    }

    private static SyncList list(String name, Instant savedAt, String... cardIds) {
        return new SyncList(name, List.of(cardIds), savedAt.toString(), savedAt.toString());
    }

    private static SyncCard card(UUID id, String front, String answer, Instant savedAt) {
        return new SyncCard(id.toString(), front, answer, savedAt.toString(), savedAt.toString());
    }

    private static SyncAnswer answer(String id, String mode) {
        return new SyncAnswer(id, "hiragana:3042", true, 900, mode, Instant.now().toString());
    }

    private SyncOutcome merge(SyncSnapshot incoming) throws Exception {
        MockHttpServletResponse response = mvc.perform(post("/api/sync/merge").contentType(MediaType.APPLICATION_JSON).content(gson.toJson(incoming))).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        return gson.fromJson(response.getContentAsString(), SyncOutcome.class);
    }

    private SyncSnapshot snapshotOverHttp() throws Exception {
        MockHttpServletResponse response = mvc.perform(get("/api/sync/snapshot")).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        return gson.fromJson(response.getContentAsString(), SyncSnapshot.class);
    }

    @Test
    void theSummaryCountsWhatIsStored() throws Exception {
        lists.save(new SaveListRequest("Verbs", List.of("hiragana:3042")));
        cards.save(new SaveCustomCardRequest(null, "front", "answer"));
        answers.record(new RecordAnswerRequest("a-1", "hiragana:3042", true, 800, "reading"));
        answers.record(new RecordAnswerRequest("a-2", "hiragana:3044", false, 1200, "reading"));

        MockHttpServletResponse response = mvc.perform(get("/api/sync/summary")).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        SyncSummary summary = gson.fromJson(response.getContentAsString(), SyncSummary.class);
        assertThat(summary.answers()).isEqualTo(2);
        assertThat(summary.lists()).isEqualTo(1);
        assertThat(summary.customCards()).isEqualTo(1);
        assertThat(summary.lastAnswerAt()).isNotNull();
        assertThat(summary.lastSyncAt()).isNull(); // nothing has synced yet
    }

    @Test
    void theSnapshotCarriesEverythingIncludingWhatWasDeleted() throws Exception {
        lists.save(new SaveListRequest("Verbs", List.of("hiragana:3042", "kanji:65e5")));
        lists.save(new SaveListRequest("Gone", List.of("hiragana:3044")));
        cards.save(new SaveCustomCardRequest(null, "front", "answer"));
        answers.record(new RecordAnswerRequest("a-1", "hiragana:3042", true, 800, "reading"));
        lists.delete(lists.all().stream().filter(l -> l.name().equals("Gone")).findFirst().orElseThrow().id());

        SyncSnapshot snap = snapshotOverHttp();
        assertThat(snap.getLists()).extracting(SyncList::getName).containsExactly("Verbs");
        assertThat(snap.getLists().get(0).getCards()).containsExactly("hiragana:3042", "kanji:65e5");
        assertThat(snap.getCustomCards()).extracting(SyncCard::getFront).containsExactly("front");
        assertThat(snap.getAnswers()).extracting(SyncAnswer::getId).containsExactly("a-1");
        assertThat(snap.getDeletions()).extracting(SyncDeletion::getKind, SyncDeletion::getKey).containsExactly(org.assertj.core.groups.Tuple.tuple("list", "gone"));
    }

    @Test
    void mergingAddsWhatIsMissingAndMergingAgainChangesNothing() throws Exception {
        UUID cardId = UUID.randomUUID();
        SyncSnapshot incoming = snapshot(
                List.of(list("Animals", hoursFromNow(-1), "custom:" + cardId, "kanji:72ac")),
                List.of(card(cardId, "neko", "cat", hoursFromNow(-1))),
                List.of(answer("e-1", "reading"), answer("e-2", "kana"), answer("e-bad", "dancing")), // the last one is not a mode this app knows
                List.of());

        SyncOutcome first = merge(incoming);
        assertThat(first.getAnswers()).isEqualTo(2);
        assertThat(first.getLists()).isEqualTo(1);
        assertThat(first.getCards()).isEqualTo(1);

        SyncOutcome second = merge(incoming);
        assertThat(second.getAnswers()).isZero();
        assertThat(second.getLists()).isZero();
        assertThat(second.getCards()).isZero();
        assertThat(second.getRemoved()).isZero();

        SyncSnapshot now = snapshotOverHttp();
        assertThat(now.getCustomCards().get(0).getId()).isEqualTo(cardId.toString()); // the card kept its id, so the list still points at it
        assertThat(now.getLists().get(0).getCards()).contains("custom:" + cardId);
        assertThat(now.getAnswers()).hasSize(2);
    }

    @Test
    void theNewerVersionOfAListWinsInBothDirections() throws Exception {
        lists.save(new SaveListRequest("Verbs", List.of("hiragana:3042")));

        // an older copy (different capitals, the same list) changes nothing
        assertThat(merge(snapshot(List.of(list("VERBS", hoursFromNow(-5), "hiragana:3044")), List.of(), List.of(), List.of())).getLists()).isZero();
        assertThat(lists.all()).hasSize(1);
        assertThat(lists.all().get(0).cards()).containsExactly("hiragana:3042");

        // a newer copy replaces the cards and the name, and there is still one list
        assertThat(merge(snapshot(List.of(list("VERBS", hoursFromNow(5), "hiragana:3044", "hiragana:3046")), List.of(), List.of(), List.of())).getLists()).isEqualTo(1);
        assertThat(lists.all()).hasSize(1);
        assertThat(lists.all().get(0).name()).isEqualTo("VERBS");
        assertThat(lists.all().get(0).cards()).containsExactly("hiragana:3044", "hiragana:3046");
    }

    @Test
    void theNewerVersionOfACardWins() throws Exception {
        String id = cards.save(new SaveCustomCardRequest(null, "old front", "old answer")).card().id();

        assertThat(merge(snapshot(List.of(), List.of(card(UUID.fromString(id), "ancient", "ancient", hoursFromNow(-9))), List.of(), List.of())).getCards()).isZero();
        assertThat(cards.all().get(0).front()).isEqualTo("old front");

        assertThat(merge(snapshot(List.of(), List.of(card(UUID.fromString(id), "new front", "new answer", hoursFromNow(9))), List.of(), List.of())).getCards()).isEqualTo(1);
        assertThat(cards.all()).hasSize(1);
        assertThat(cards.all().get(0).front()).isEqualTo("new front");
    }

    @Test
    void aDeletionFromTheOtherSideRemovesTheItemAndIsRemembered() throws Exception {
        lists.save(new SaveListRequest("Verbs", List.of("hiragana:3042")));
        String cardId = cards.save(new SaveCustomCardRequest(null, "front", "answer")).card().id();

        SyncOutcome outcome = merge(snapshot(List.of(), List.of(), List.of(), List.of(
                new SyncDeletion("list", "verbs", hoursFromNow(1).toString()),
                new SyncDeletion("card", cardId, hoursFromNow(1).toString()))));
        assertThat(outcome.getRemoved()).isEqualTo(2);
        assertThat(lists.all()).isEmpty();
        assertThat(cards.all()).isEmpty();
        assertThat(deletions.count()).isEqualTo(2); // remembered, so an old copy that arrives later does not bring them back
    }

    @Test
    void aDeletionOlderThanTheItemIsIgnored() throws Exception {
        lists.save(new SaveListRequest("Verbs", List.of("hiragana:3042")));

        SyncOutcome outcome = merge(snapshot(List.of(), List.of(), List.of(), List.of(new SyncDeletion("list", "verbs", hoursFromNow(-3).toString()))));
        assertThat(outcome.getRemoved()).isZero();
        assertThat(lists.all()).hasSize(1); // saved after it was deleted elsewhere: it stays
        assertThat(deletions.count()).isZero();
    }

    @Test
    void aDeletedListDoesNotComeBackFromAnOldCopyButDoesFromANewerOne() throws Exception {
        lists.save(new SaveListRequest("Verbs", List.of("hiragana:3042")));
        lists.delete(lists.all().get(0).id());
        assertThat(lists.all()).isEmpty();

        assertThat(merge(snapshot(List.of(list("Verbs", hoursFromNow(-2), "hiragana:3042")), List.of(), List.of(), List.of())).getLists()).isZero();
        assertThat(lists.all()).isEmpty();

        assertThat(merge(snapshot(List.of(list("Verbs", hoursFromNow(2), "hiragana:3042")), List.of(), List.of(), List.of())).getLists()).isEqualTo(1);
        assertThat(lists.all()).hasSize(1);
        assertThat(deletions.findById(Deletion.idOf(Deletion.LIST, "verbs"))).isEmpty(); // it is back, so it is no longer a deleted list
    }

    @Test
    void savingAListAgainAfterDeletingItClearsTheNote() {
        lists.save(new SaveListRequest("Verbs", List.of("hiragana:3042")));
        lists.delete(lists.all().get(0).id());
        assertThat(deletions.findById(Deletion.idOf(Deletion.LIST, "verbs"))).isPresent();

        lists.save(new SaveListRequest("VERBS", List.of("hiragana:3044")));
        assertThat(deletions.findById(Deletion.idOf(Deletion.LIST, "verbs"))).isEmpty();
    }

    @Test
    void deletingACustomCardLeavesANote() {
        String id = cards.save(new SaveCustomCardRequest(null, "front", "answer")).card().id();
        cards.delete(id);
        assertThat(deletions.findById(Deletion.idOf(Deletion.CARD, id))).isPresent();
    }

    @Test
    void whatDoesNotFitTheRulesIsSkippedNotStored() throws Exception {
        String tooLong = "x".repeat(CustomCardsService.MAX_TEXT + 1);
        SyncOutcome outcome = merge(snapshot(
                List.of(list(" ", hoursFromNow(-1), "hiragana:3042"), list("Fine", hoursFromNow(-1), "x".repeat(ListsService.MAX_CARD_ID + 1))),
                List.of(card(UUID.randomUUID(), tooLong, "a", hoursFromNow(-1)), new SyncCard("not-a-uuid", "a", "b", Instant.now().toString(), Instant.now().toString())),
                Stream.of(new SyncAnswer("x".repeat(AnswersService.MAX_ID + 1), "hiragana:3042", true, 1, "reading", Instant.now().toString()),
                        new SyncAnswer("e-1", "hiragana:3042", true, 1, "reading", "yesterday-ish")).toList(),
                List.of(new SyncDeletion("planet", "mars", Instant.now().toString()))));
        assertThat(outcome.getAnswers()).isZero();
        assertThat(outcome.getLists()).isZero();
        assertThat(outcome.getCards()).isZero();
        assertThat(snapshotOverHttp().getDeletions()).isEmpty();
    }

    @Test
    void replacingMakesABackupFirstAndThenHoldsExactlyWhatWasSent() throws Exception {
        lists.save(new SaveListRequest("Old list", List.of("hiragana:3042")));
        answers.record(new RecordAnswerRequest("old-1", "hiragana:3042", true, 1, "reading"));
        SyncSnapshot replacement = snapshot(List.of(list("New list", hoursFromNow(-1), "kanji:65e5")), List.of(), List.of(answer("new-1", "reading"), answer("new-2", "meaning")), List.of());

        MockHttpServletResponse response = mvc.perform(post("/api/sync/replace").contentType(MediaType.APPLICATION_JSON).content(gson.toJson(replacement))).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        SyncOutcome outcome = gson.fromJson(response.getContentAsString(), SyncOutcome.class);

        assertThat(outcome.getBackup()).matches("backup-\\d{4}-\\d{2}-\\d{2}-\\d{6}(-\\d+)?\\.db");
        assertThat(DATA.resolve(outcome.getBackup())).exists().isNotEmptyFile();
        assertThat(lists.all()).extracting(CardListSummary::name).containsExactly("New list");
        SyncSnapshot now = snapshotOverHttp();
        assertThat(now.getAnswers()).extracting(SyncAnswer::getId).containsExactlyInAnyOrder("new-1", "new-2");
    }

    @Test
    void aReplaceThatLacksPartsIsRefusedSoABrokenRequestCannotEmptyTheServer() throws Exception {
        lists.save(new SaveListRequest("Keep me", List.of("hiragana:3042")));
        for (String body : new String[] {"{}", "{\"lists\":[]}", "{\"lists\":[],\"customCards\":[],\"answers\":[]}"}) {
            MockHttpServletResponse response = mvc.perform(post("/api/sync/replace").contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
            assertThat(response.getStatus()).as(body).isEqualTo(400);
        }
        assertThat(lists.all()).hasSize(1);
    }

    @Test
    void aSyncThatIsNotJsonIsRefused() throws Exception {
        assertThat(mvc.perform(post("/api/sync/merge").contentType(MediaType.TEXT_PLAIN).content("{}")).andReturn().getResponse().getStatus()).isEqualTo(415);
        assertThat(mvc.perform(post("/api/sync/replace").contentType(MediaType.TEXT_PLAIN).content("{}")).andReturn().getResponse().getStatus()).isEqualTo(415);
    }

    @Test
    void aMergeAndAReplaceRecordWhenTheSyncHappened() throws Exception {
        assertThat(store.summary().lastSyncAt()).isNull();
        merge(empty());
        assertThat(store.summary().lastSyncAt()).isNotNull();
    }
}
