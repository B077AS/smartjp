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

import io.github.b077as.smartjp.config.DatabaseConfig;
import io.github.b077as.smartjp.model.dto.summary.CardListSummary;
import io.github.b077as.smartjp.service.ListsService;

/** The flashcard lists through the website's door (HTTP), on a real SQLite file in a temporary folder. */
@SpringBootTest
@AutoConfigureMockMvc
class ListsApiTest {

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
    private ListsService lists;
    @Autowired
    private Gson gson;

    @BeforeEach
    void emptyTheDatabase() {
        lists.all().forEach(list -> lists.delete(list.id()));
    }

    private JsonObject save(String name, String... cards) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("name", name);
        body.add("cards", gson.toJsonTree(List.of(cards)));
        return saveRaw(body.toString());
    }

    private JsonObject saveRaw(String json) throws Exception {
        MockHttpServletResponse response = mvc.perform(post("/api/lists").contentType(MediaType.APPLICATION_JSON).content(json)).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        return gson.fromJson(response.getContentAsString(), JsonObject.class);
    }

    private List<CardListSummary> listed() throws Exception {
        String body = mvc.perform(get("/api/lists")).andReturn().getResponse().getContentAsString();
        return List.of(gson.fromJson(body, CardListSummary[].class));
    }

    @Test
    void theDatabaseFileIsCreatedAndStartsEmpty() throws Exception {
        assertThat(DATA.resolve(DatabaseConfig.FILE)).exists();
        assertThat(listed()).isEmpty();
    }

    @Test
    void aSavedListComesBackWithItsCardsInOrder() throws Exception {
        JsonObject reply = save("Row k", "hira:ka", "hira:ki", "hira:ku");
        assertThat(reply.get("result").getAsString()).isEqualTo("created");

        List<CardListSummary> all = listed();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).name()).isEqualTo("Row k");
        assertThat(all.get(0).cards()).containsExactly("hira:ka", "hira:ki", "hira:ku");
        assertThat(all.get(0).id()).isEqualTo(reply.getAsJsonObject("list").get("id").getAsString());
    }

    @Test
    void theSameNameReplacesTheListIgnoringCaseAndKeepsItsPlace() throws Exception {
        save("First", "a");
        save("Second", "b");
        JsonObject again = save("  FIRST ", "c", "d", "c"); // spaces trimmed, a repeated card counted once

        assertThat(again.get("result").getAsString()).isEqualTo("replaced");
        List<CardListSummary> all = listed();
        assertThat(all).extracting(CardListSummary::name).containsExactly("FIRST", "Second");
        assertThat(all.get(0).cards()).containsExactly("c", "d");
        assertThat(all.get(0).id()).isEqualTo(again.getAsJsonObject("list").get("id").getAsString());
    }

    @Test
    void aNameIsCleanedAndCutAtFortyCharacters() throws Exception {
        JsonObject reply = save("a   b\t c " + "x".repeat(60), "a");
        String name = reply.getAsJsonObject("list").get("name").getAsString();
        assertThat(name).hasSize(ListsService.MAX_NAME).startsWith("a b c xxx");
    }

    @Test
    void refusedSavesSayWhyAndStoreNothing() throws Exception {
        assertThat(save("   ", "a").get("result").getAsString()).isEqualTo("empty_name");
        assertThat(saveRaw("{\"cards\":[\"a\"]}").get("result").getAsString()).isEqualTo("empty_name");
        assertThat(save("Empty").get("result").getAsString()).isEqualTo("no_cards");
        assertThat(saveRaw("{\"name\":\"No cards field\"}").get("result").getAsString()).isEqualTo("no_cards");
        assertThat(save("Long id", "x".repeat(ListsService.MAX_CARD_ID + 1)).get("result").getAsString()).isEqualTo("bad_cards");
        assertThat(save("Blank id", " ").get("result").getAsString()).isEqualTo("bad_cards");
        assertThat(listed()).isEmpty();
    }

    @Test
    void thereIsALimitOnTheNumberOfLists() throws Exception {
        for (int i = 0; i < ListsService.MAX_LISTS; i++) {
            assertThat(save("List " + i, "a").get("result").getAsString()).isEqualTo("created");
        }
        assertThat(save("One too many", "a").get("result").getAsString()).isEqualTo("too_many");
        // Saving under an existing name still works at the limit: it replaces, it does not add.
        assertThat(save("list 0", "b").get("result").getAsString()).isEqualTo("replaced");
        assertThat(listed()).hasSize(ListsService.MAX_LISTS);
    }

    @Test
    void deletingRemovesTheListAndDeletingAnUnknownOneIsNotAnError() throws Exception {
        String id = save("Gone soon", "a").getAsJsonObject("list").get("id").getAsString();
        assertThat(mvc.perform(delete("/api/lists/" + id)).andReturn().getResponse().getStatus()).isEqualTo(204);
        assertThat(listed()).isEmpty();
        assertThat(mvc.perform(delete("/api/lists/" + id)).andReturn().getResponse().getStatus()).isEqualTo(204);
    }

    @Test
    void aSaveThatIsNotJsonIsRefused() throws Exception {
        // What another site's page could send without the browser asking first (a form, text/plain) never reaches the service.
        int plain = mvc.perform(post("/api/lists").contentType(MediaType.TEXT_PLAIN).content("{\"name\":\"x\",\"cards\":[\"a\"]}")).andReturn().getResponse().getStatus();
        int form = mvc.perform(post("/api/lists").contentType(MediaType.APPLICATION_FORM_URLENCODED).param("name", "x")).andReturn().getResponse().getStatus();
        assertThat(plain).isEqualTo(415);
        assertThat(form).isEqualTo(415);
        assertThat(listed()).isEmpty();
    }

    @Test
    void aBrokenBodyIsABadRequestNotAServerError() throws Exception {
        int status = mvc.perform(post("/api/lists").contentType(MediaType.APPLICATION_JSON).content("{not json")).andReturn().getResponse().getStatus();
        assertThat(status).isEqualTo(400);
    }
}
