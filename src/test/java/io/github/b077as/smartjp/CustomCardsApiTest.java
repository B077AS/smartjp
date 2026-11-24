package io.github.b077as.smartjp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
import com.google.gson.JsonObject;

import io.github.b077as.smartjp.model.dto.summary.CustomCardSummary;
import io.github.b077as.smartjp.service.CustomCardsService;

/** The cards the user writes, through the website's door (HTTP), on a real SQLite file in a temporary folder. */
@SpringBootTest
@AutoConfigureMockMvc
class CustomCardsApiTest {

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
    private CustomCardsService cards;
    @Autowired
    private Gson gson;

    @BeforeEach
    void emptyTheDatabase() {
        cards.all().forEach(card -> cards.delete(card.id()));
    }

    private JsonObject save(String id, String front, String answer) throws Exception {
        JsonObject body = new JsonObject();
        if (id != null) {
            body.addProperty("id", id);
        }
        if (front != null) {
            body.addProperty("front", front);
        }
        if (answer != null) {
            body.addProperty("answer", answer);
        }
        return saveRaw(body.toString());
    }

    private JsonObject saveRaw(String json) throws Exception {
        MockHttpServletResponse response = mvc.perform(post("/api/custom-cards").contentType(MediaType.APPLICATION_JSON).content(json)).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        return gson.fromJson(response.getContentAsString(), JsonObject.class);
    }

    private String result(JsonObject reply) {
        return reply.get("result").getAsString();
    }

    private List<CustomCardSummary> listed() throws Exception {
        String body = mvc.perform(get("/api/custom-cards")).andReturn().getResponse().getContentAsString();
        return List.of(gson.fromJson(body, CustomCardSummary[].class));
    }

    @Test
    void aNewCardComesBackWithItsTextsAsWritten() throws Exception {
        JsonObject reply = save(null, "  What is the capital of Japan?\nThink of the city, not the region.  ", "Tokyo");
        assertThat(result(reply)).isEqualTo("created");

        List<CustomCardSummary> all = listed();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).front()).isEqualTo("What is the capital of Japan?\nThink of the city, not the region."); // ends trimmed, the line break kept
        assertThat(all.get(0).answer()).isEqualTo("Tokyo");
        assertThat(all.get(0).id()).isEqualTo(reply.getAsJsonObject("card").get("id").getAsString());
    }

    @Test
    void japaneseAndLongTextSurvivesTheDatabase() throws Exception {
        String front = "今日は天気がいいので、公園に散歩に行きましょう。".repeat(10);
        assertThat(result(save(null, front, "Nice weather today, so let's go for a walk in the park."))).isEqualTo("created");
        assertThat(listed().get(0).front()).isEqualTo(front);
    }

    @Test
    void editingKeepsTheIdAndThePlace() throws Exception {
        String first = save(null, "one", "1").getAsJsonObject("card").get("id").getAsString();
        save(null, "two", "2");

        JsonObject again = save(first, "uno", "1!");
        assertThat(result(again)).isEqualTo("updated");
        List<CustomCardSummary> all = listed();
        assertThat(all).extracting(CustomCardSummary::front).containsExactly("uno", "two");
        assertThat(all.get(0).id()).isEqualTo(first);
        assertThat(all.get(0).answer()).isEqualTo("1!");
    }

    @Test
    void anUnknownIdIsNotFoundAndNothingIsCreated() throws Exception {
        assertThat(result(save("not-an-id", "a", "b"))).isEqualTo("not_found");
        assertThat(result(save("7b2f3a1c-0000-4000-8000-000000000000", "a", "b"))).isEqualTo("not_found");
        assertThat(listed()).isEmpty();
    }

    @Test
    void refusedSavesSayWhyAndStoreNothing() throws Exception {
        assertThat(result(save(null, "   ", "answer"))).isEqualTo("empty_front");
        assertThat(result(save(null, null, "answer"))).isEqualTo("empty_front");
        assertThat(result(save(null, "front", " \n "))).isEqualTo("empty_answer");
        assertThat(result(save(null, "front", null))).isEqualTo("empty_answer");
        assertThat(result(save(null, "x".repeat(CustomCardsService.MAX_TEXT + 1), "a"))).isEqualTo("too_long");
        assertThat(result(save(null, "a", "x".repeat(CustomCardsService.MAX_TEXT + 1)))).isEqualTo("too_long");
        assertThat(listed()).isEmpty();
    }

    @Test
    void theLongestTextIsAccepted() throws Exception {
        assertThat(result(save(null, "x".repeat(CustomCardsService.MAX_TEXT), "y".repeat(CustomCardsService.MAX_TEXT)))).isEqualTo("created");
    }

    @Test
    void aLongTextIsRefusedNotCutOff() throws Exception {
        // The limit counts characters, not Java chars: an emoji outside the basic plane is one character.
        assertThat(result(save(null, "😀".repeat(CustomCardsService.MAX_TEXT), "a"))).isEqualTo("created");
        assertThat(result(save(null, "😀".repeat(CustomCardsService.MAX_TEXT + 1), "a"))).isEqualTo("too_long");
    }

    @Test
    void deletingRemovesTheCardAndDeletingAnUnknownOneIsNotAnError() throws Exception {
        String id = save(null, "a", "b").getAsJsonObject("card").get("id").getAsString();
        assertThat(mvc.perform(delete("/api/custom-cards/" + id)).andReturn().getResponse().getStatus()).isEqualTo(204);
        assertThat(listed()).isEmpty();
        assertThat(mvc.perform(delete("/api/custom-cards/" + id)).andReturn().getResponse().getStatus()).isEqualTo(204);
        assertThat(mvc.perform(delete("/api/custom-cards/nonsense")).andReturn().getResponse().getStatus()).isEqualTo(204);
    }

    @Test
    void aSaveThatIsNotJsonIsRefused() throws Exception {
        int plain = mvc.perform(post("/api/custom-cards").contentType(MediaType.TEXT_PLAIN).content("{\"front\":\"x\",\"answer\":\"y\"}")).andReturn().getResponse().getStatus();
        assertThat(plain).isEqualTo(415);
        assertThat(listed()).isEmpty();
    }
}
