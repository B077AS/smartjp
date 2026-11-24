package io.github.b077as.smartjp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
import com.google.gson.JsonObject;

import io.github.b077as.smartjp.model.dto.summary.CardStatsSummary;
import io.github.b077as.smartjp.repository.AnswerEventRepository;

/** The answer log, through the website's door (HTTP), on a real SQLite file in a temporary folder. */
@SpringBootTest
@AutoConfigureMockMvc
class AnswersApiTest {

    private static final Path DATA = tempFolder();

    private static Path tempFolder() {
        try {
            return Files.createTempDirectory("smartjp-test-");
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
    private AnswerEventRepository events;
    @Autowired
    private Gson gson;

    private String record(String id, String cardId, boolean correct, String mode) throws Exception {
        JsonObject body = new JsonObject();
        if (id != null) {
            body.addProperty("id", id);
        }
        if (cardId != null) {
            body.addProperty("cardId", cardId);
        }
        body.addProperty("correct", correct);
        body.addProperty("ms", 1200);
        if (mode != null) {
            body.addProperty("mode", mode);
        }
        return recordRaw(body.toString());
    }

    private String recordRaw(String json) throws Exception {
        MockHttpServletResponse response = mvc.perform(post("/api/answers").contentType(MediaType.APPLICATION_JSON).content(json)).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        return gson.fromJson(response.getContentAsString(), JsonObject.class).get("result").getAsString();
    }

    private List<CardStatsSummary> stats() throws Exception {
        String body = mvc.perform(get("/api/answers/stats")).andReturn().getResponse().getContentAsString();
        return List.of(gson.fromJson(body, CardStatsSummary[].class));
    }

    private CardStatsSummary statsOf(String cardId) throws Exception {
        return stats().stream().filter(s -> s.cardId().equals(cardId)).findFirst().orElse(null);
    }

    @Test
    void totalsCountEveryAnswerAndTheRightOnes() throws Exception {
        events.deleteAll();
        assertThat(record("t1-a", "hira:ka", false, "reading")).isEqualTo("recorded");
        assertThat(record("t1-b", "hira:ka", false, "kana")).isEqualTo("recorded");
        assertThat(record("t1-c", "hira:ka", true, "reading")).isEqualTo("recorded");
        record("t1-d", "kanji:65e5", true, "meaning");

        CardStatsSummary ka = statsOf("hira:ka");
        assertThat(ka.seen()).isEqualTo(3);
        assertThat(ka.correct()).isEqualTo(1);
        CardStatsSummary day = statsOf("kanji:65e5");
        assertThat(day.seen()).isEqualTo(1);
        assertThat(day.correct()).isEqualTo(1);
        assertThat(stats()).hasSize(2);
    }

    @Test
    void theSameAnswerSentTwiceIsStoredOnce() throws Exception {
        events.deleteAll();
        assertThat(record("t2-a", "hira:shi", false, "reading")).isEqualTo("recorded");
        assertThat(record("t2-a", "hira:shi", false, "reading")).isEqualTo("duplicate");
        assertThat(statsOf("hira:shi").seen()).isEqualTo(1);
    }

    @Test
    void aCustomCardAndEveryModeAreAccepted() throws Exception {
        events.deleteAll();
        String custom = "custom:123e4567-e89b-12d3-a456-426614174000";
        assertThat(record("t3-a", custom, true, "reading")).isEqualTo("recorded");
        assertThat(record("t3-b", custom, false, "flip")).isEqualTo("recorded");
        assertThat(statsOf(custom).seen()).isEqualTo(2);
    }

    @Test
    void badInputIsAnsweredNotStored() throws Exception {
        events.deleteAll();
        assertThat(record(null, "hira:a", true, "reading")).isEqualTo("bad_id");
        assertThat(record("t4-a", null, true, "reading")).isEqualTo("bad_card");
        assertThat(record("t4-b", "x".repeat(65), true, "reading")).isEqualTo("bad_card");
        assertThat(record("t4-c", "hira:a", true, "shout")).isEqualTo("bad_mode");
        assertThat(record("t4-d", "hira:a", true, null)).isEqualTo("bad_mode");
        assertThat(stats()).isEmpty();
    }

    @Test
    void aSillyTimeIsClampedNotRefused() throws Exception {
        events.deleteAll();
        assertThat(recordRaw("{\"id\":\"t5-a\",\"cardId\":\"hira:a\",\"correct\":true,\"ms\":-5,\"mode\":\"reading\"}")).isEqualTo("recorded");
        assertThat(recordRaw("{\"id\":\"t5-b\",\"cardId\":\"hira:a\",\"correct\":true,\"ms\":999999999999,\"mode\":\"reading\"}")).isEqualTo("recorded");
        assertThat(events.findById("t5-a").orElseThrow().getMs()).isZero();
        assertThat(events.findById("t5-b").orElseThrow().getMs()).isEqualTo(60L * 60 * 1000);
    }

    @Test
    void textPlainIsRefused() throws Exception {
        int status = mvc.perform(post("/api/answers").contentType(MediaType.TEXT_PLAIN).content("{}")).andReturn().getResponse().getStatus();
        assertThat(status).isEqualTo(415);
    }
}
