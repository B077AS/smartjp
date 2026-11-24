package io.github.b077as.smartjp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.google.gson.Gson;

import io.github.b077as.smartjp.model.dto.request.ConnectRequest;
import io.github.b077as.smartjp.model.dto.request.RecordAnswerRequest;
import io.github.b077as.smartjp.model.dto.request.RunSyncRequest;
import io.github.b077as.smartjp.model.dto.request.SaveCustomCardRequest;
import io.github.b077as.smartjp.model.dto.request.SaveListRequest;
import io.github.b077as.smartjp.model.dto.response.CompareResponse;
import io.github.b077as.smartjp.model.dto.response.RunSyncResponse;
import io.github.b077as.smartjp.model.dto.summary.CardListSummary;
import io.github.b077as.smartjp.model.dto.summary.CustomCardSummary;
import io.github.b077as.smartjp.model.dto.sync.SyncSnapshot;
import io.github.b077as.smartjp.security.AccessKeyService;
import io.github.b077as.smartjp.service.AnswersService;
import io.github.b077as.smartjp.service.AppSettingsService;
import io.github.b077as.smartjp.service.CustomCardsService;
import io.github.b077as.smartjp.service.ListsService;
import io.github.b077as.smartjp.service.SyncClient;
import io.github.b077as.smartjp.service.SyncDesktopService;
import io.github.b077as.smartjp.service.SyncKeyVault;
import io.github.b077as.smartjp.service.SyncService;
import io.github.b077as.smartjp.service.SyncStore;

/**
 * A whole sync, over real HTTP: the website runs here with its own SQLite file, and a second application context (no web server,
 * its own file) plays the desktop app, with the same SyncDesktopService the real desktop uses, except that the access key sits in
 * a plain object instead of the operating system's keystore. Nothing is mocked between the two.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SyncEndToEndTest {

    private static final Path SERVER_DATA = tempFolder("smartjp-e2e-server-");
    private static final Path DESKTOP_DATA = tempFolder("smartjp-e2e-desktop-");
    private static ConfigurableApplicationContext desktop;

    private static Path tempFolder(String prefix) {
        try {
            return Files.createTempDirectory(prefix);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void dataFolder(DynamicPropertyRegistry registry) {
        registry.add("smartjp.data-dir", SERVER_DATA::toString);
    }

    /** The access key in memory (the real app uses the system keystore). */
    private static class MemoryVault implements SyncKeyVault {
        private String key;

        @Override
        public Optional<String> get() {
            return Optional.ofNullable(key);
        }

        @Override
        public void put(String value) {
            key = value;
        }

        @Override
        public void delete() {
            key = null;
        }
    }

    @Value("${local.server.port}")
    private int port;
    @Autowired
    private Gson gson;
    @Autowired
    private AccessKeyService keys;
    @Autowired
    private SyncStore serverStore;
    @Autowired
    private SyncService serverSync;
    @Autowired
    private ListsService serverLists;
    @Autowired
    private CustomCardsService serverCards;
    @Autowired
    private AnswersService serverAnswers;
    @Autowired
    private AppSettingsService serverSettings;

    private SyncDesktopService desk;
    private ListsService deskLists;
    private CustomCardsService deskCards;
    private AnswersService deskAnswers;
    private SyncService deskSync;
    private SyncStore deskStore;

    @BeforeEach
    void bothSidesEmptyAndNotConnected() {
        if (desktop == null) {
            desktop = new SpringApplicationBuilder(SmartJpApplication.class)
                    .web(WebApplicationType.NONE)
                    .properties("spring.main.banner-mode=off")
                    // A command line argument, not a default property: default properties lose against application.properties, which would
                    // have pointed this context at ./data, the website's real folder.
                    .run("--smartjp.data-dir=" + DESKTOP_DATA);
        assertThat(desktop.getEnvironment().getProperty("smartjp.data-dir")).isEqualTo(DESKTOP_DATA.toString()); // never touch anything but the temp folder
        }
        deskLists = desktop.getBean(ListsService.class);
        deskCards = desktop.getBean(CustomCardsService.class);
        deskAnswers = desktop.getBean(AnswersService.class);
        deskSync = desktop.getBean(SyncService.class);
        deskStore = desktop.getBean(SyncStore.class);
        desk = new SyncDesktopService(new SyncClient(gson), deskSync, desktop.getBean(AppSettingsService.class), new MemoryVault());

        SyncSnapshot nothing = new SyncSnapshot(List.of(), List.of(), List.of(), List.of());
        serverStore.replaceAll(nothing);
        deskStore.replaceAll(nothing);
        desktop.getBean(AppSettingsService.class).remove(SyncStore.LAST_SYNC);
        serverSettings.remove(SyncStore.LAST_SYNC);
    }

    @AfterAll
    static void closeTheDesktop() {
        if (desktop != null) {
            desktop.close();
        }
    }

    private String address() {
        return "http://127.0.0.1:" + port + "/";
    }

    private void connect() {
        assertThat(desk.connect(new ConnectRequest(address(), keys.key())).result()).isEqualTo("connected");
    }

    private RunSyncResponse run(String action) {
        return desk.run(new RunSyncRequest(action));
    }

    @Test
    void connectingChecksTheAddressAndTheKeyAndKeepsThemOnlyWhenTheyWork() {
        assertThat(desk.status().connected()).isFalse();
        assertThat(desk.connect(new ConnectRequest("", keys.key())).result()).isEqualTo("bad_address");
        assertThat(desk.connect(new ConnectRequest("ftp://example.org", keys.key())).result()).isEqualTo("bad_address");
        assertThat(desk.connect(new ConnectRequest(address(), "")).result()).isEqualTo("bad_key");
        assertThat(desk.connect(new ConnectRequest(address(), "not-the-key")).result()).isEqualTo("bad_key");
        assertThat(desk.connect(new ConnectRequest("http://127.0.0.1:1", keys.key())).result()).isEqualTo("unreachable");
        assertThat(desk.status().connected()).isFalse(); // none of those was kept

        // no scheme means https, and this test server speaks plain http
        assertThat(desk.connect(new ConnectRequest("127.0.0.1:" + port, keys.key())).result()).isEqualTo("unreachable");

        connect();
        assertThat(desk.status().connected()).isTrue();
        assertThat(desk.status().address()).isEqualTo("http://127.0.0.1:" + port); // the trailing slash is dropped
        desk.disconnect();
        assertThat(run("merge").result()).isEqualTo("not_connected");
        assertThat(desk.compare().result()).isEqualTo("not_connected");
    }

    @Test
    void anUnknownActionIsRefusedBeforeAnythingHappens() {
        connect();
        assertThat(run("explode").result()).isEqualTo("bad_action");
        assertThat(run(null).result()).isEqualTo("bad_action");
    }

    @Test
    void compareShowsBothSides() {
        deskAnswers.record(new RecordAnswerRequest("d-1", "hiragana:3042", true, 1, "reading"));
        serverAnswers.record(new RecordAnswerRequest("s-1", "hiragana:3044", true, 1, "reading"));
        serverAnswers.record(new RecordAnswerRequest("s-2", "hiragana:3046", false, 1, "reading"));
        connect();

        CompareResponse compare = desk.compare();
        assertThat(compare.result()).isEqualTo("ok");
        assertThat(compare.local().answers()).isEqualTo(1);
        assertThat(compare.remote().answers()).isEqualTo(2);
    }

    @Test
    void mergingGivesBothSidesTheSameDataAndMergingAgainChangesNothing() {
        deskAnswers.record(new RecordAnswerRequest("shared", "hiragana:3042", true, 1, "reading"));
        deskAnswers.record(new RecordAnswerRequest("d-1", "hiragana:3044", false, 1, "reading"));
        serverAnswers.record(new RecordAnswerRequest("shared", "hiragana:3042", true, 1, "reading"));
        serverAnswers.record(new RecordAnswerRequest("s-1", "kanji:65e5", true, 1, "meaning"));
        deskLists.save(new SaveListRequest("Desktop list", List.of("hiragana:3042")));
        serverLists.save(new SaveListRequest("Server list", List.of("kanji:65e5")));
        String deskCard = deskCards.save(new SaveCustomCardRequest(null, "desktop front", "desktop answer")).card().id();
        serverCards.save(new SaveCustomCardRequest(null, "server front", "server answer"));
        deskLists.save(new SaveListRequest("Uses a card", List.of("custom:" + deskCard)));
        connect();

        RunSyncResponse first = run("merge");
        assertThat(first.result()).isEqualTo("done");
        assertThat(first.sent().answers()).isEqualTo(1); // d-1 went to the server
        assertThat(first.received().answers()).isEqualTo(1); // s-1 came here
        assertThat(first.backup()).isNull(); // a merge overwrites nothing

        SyncSnapshot onDesktop = deskSync.snapshot();
        SyncSnapshot onServer = serverSync.snapshot();
        assertThat(onDesktop.getAnswers()).hasSize(3);
        assertThat(onServer.getAnswers()).hasSize(3);
        assertThat(deskLists.all()).extracting(CardListSummary::name).containsExactlyInAnyOrder("Desktop list", "Server list", "Uses a card");
        assertThat(serverLists.all()).extracting(CardListSummary::name).containsExactlyInAnyOrder("Desktop list", "Server list", "Uses a card");
        assertThat(deskCards.all()).extracting(CustomCardSummary::front).containsExactlyInAnyOrder("desktop front", "server front");
        assertThat(serverCards.all()).extracting(CustomCardSummary::id).containsExactlyInAnyOrderElementsOf(deskCards.all().stream().map(CustomCardSummary::id).toList()); // the same cards, the same ids
        assertThat(serverLists.all().stream().filter(l -> l.name().equals("Uses a card")).findFirst().orElseThrow().cards()).containsExactly("custom:" + deskCard);

        RunSyncResponse second = run("merge");
        assertThat(second.result()).isEqualTo("done");
        assertThat(second.sent().answers()).isZero();
        assertThat(second.sent().lists()).isZero();
        assertThat(second.sent().cards()).isZero();
        assertThat(second.received().answers()).isZero();
        assertThat(second.received().lists()).isZero();
        assertThat(second.received().cards()).isZero();
    }

    @Test
    void aDeletionTravelsAndTheItemDoesNotComeBack() {
        deskLists.save(new SaveListRequest("Temporary", List.of("hiragana:3042")));
        String cardId = deskCards.save(new SaveCustomCardRequest(null, "front", "answer")).card().id();
        connect();
        run("merge");
        assertThat(serverLists.all()).hasSize(1);
        assertThat(serverCards.all()).hasSize(1);

        deskLists.delete(deskLists.all().get(0).id());
        deskCards.delete(cardId);
        RunSyncResponse merged = run("merge");
        assertThat(merged.result()).isEqualTo("done");
        assertThat(merged.sent().removed()).isEqualTo(2);
        assertThat(serverLists.all()).isEmpty();
        assertThat(serverCards.all()).isEmpty();

        run("merge"); // the server still knows them as deleted: nothing returns to the desktop
        assertThat(deskLists.all()).isEmpty();
        assertThat(deskCards.all()).isEmpty();
    }

    @Test
    void pullMakesTheDesktopACopyOfTheServerAfterABackup() {
        deskAnswers.record(new RecordAnswerRequest("only-here", "hiragana:3042", true, 1, "reading"));
        deskLists.save(new SaveListRequest("Only on the desktop", List.of("hiragana:3042")));
        serverAnswers.record(new RecordAnswerRequest("on-server", "kanji:65e5", true, 1, "meaning"));
        serverLists.save(new SaveListRequest("On the server", List.of("kanji:65e5")));
        connect();

        RunSyncResponse pulled = run("pull");
        assertThat(pulled.result()).isEqualTo("done");
        assertThat(pulled.backupSide()).isEqualTo("desktop");
        assertThat(DESKTOP_DATA.resolve(pulled.backup())).exists().isNotEmptyFile();
        assertThat(pulled.received().answers()).isEqualTo(1);

        assertThat(deskSync.snapshot().getAnswers()).extracting(a -> a.getId()).containsExactly("on-server");
        assertThat(deskLists.all()).extracting(CardListSummary::name).containsExactly("On the server");
        assertThat(serverLists.all()).extracting(CardListSummary::name).containsExactly("On the server"); // the server is untouched
    }

    @Test
    void pushMakesTheServerACopyOfTheDesktopAfterTheServerSavedABackup() {
        deskAnswers.record(new RecordAnswerRequest("on-desktop", "hiragana:3042", true, 1, "reading"));
        deskLists.save(new SaveListRequest("On the desktop", List.of("hiragana:3042")));
        serverAnswers.record(new RecordAnswerRequest("only-there", "kanji:65e5", true, 1, "meaning"));
        serverLists.save(new SaveListRequest("Only on the server", List.of("kanji:65e5")));
        connect();

        RunSyncResponse pushed = run("push");
        assertThat(pushed.result()).isEqualTo("done");
        assertThat(pushed.backupSide()).isEqualTo("server");
        assertThat(SERVER_DATA.resolve(pushed.backup())).exists().isNotEmptyFile();

        assertThat(serverSync.snapshot().getAnswers()).extracting(a -> a.getId()).containsExactly("on-desktop");
        assertThat(serverLists.all()).extracting(CardListSummary::name).containsExactly("On the desktop");
        assertThat(deskLists.all()).extracting(CardListSummary::name).containsExactly("On the desktop");
    }

    @Test
    void aWrongKeyAfterTheServerGotANewOneIsReportedNotRetriedBlindly() {
        connect();
        assertThat(keys.renew()).isTrue();
        assertThat(run("merge").result()).isEqualTo("bad_key");
        CompareResponse compare = desk.compare();
        assertThat(compare.result()).isEqualTo("bad_key");
        assertThat(compare.local()).isNotNull(); // this desktop is still known
        assertThat(compare.remote()).isNull();
    }
}
