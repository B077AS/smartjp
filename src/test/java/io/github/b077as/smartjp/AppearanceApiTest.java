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

import io.github.b077as.smartjp.model.dto.sync.SyncSnapshot;
import io.github.b077as.smartjp.service.AppSettingsService;

/** The accent colour kept in the database, through the website's door, and the promise that a sync never carries or wipes it. */
@SpringBootTest
@AutoConfigureMockMvc
class AppearanceApiTest {

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
    private AppSettingsService settings;
    @Autowired
    private Gson gson;

    private JsonObject read() throws Exception {
        MockHttpServletResponse response = mvc.perform(get("/api/appearance")).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        return gson.fromJson(response.getContentAsString(), JsonObject.class);
    }

    private JsonObject save(String json) throws Exception {
        MockHttpServletResponse response = mvc.perform(post("/api/appearance").contentType(MediaType.APPLICATION_JSON).content(json)).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        return gson.fromJson(response.getContentAsString(), JsonObject.class);
    }

    @Test
    void nothingIsKeptUntilTheUserChooses() throws Exception {
        settings.remove("appearance.accent");
        assertThat(read().get("accent").isJsonNull()).isTrue();
    }

    @Test
    void aChosenAccentIsKeptAndReadBack() throws Exception {
        assertThat(save("{\"accent\":\"indigo\"}").get("result").getAsString()).isEqualTo("saved");
        assertThat(read().get("accent").getAsString()).isEqualTo("indigo");
        save("{\"accent\":\"pine\"}");
        assertThat(read().get("accent").getAsString()).isEqualTo("pine");
    }

    @Test
    void aBadKeyIsRefusedAndTheOldChoiceStays() throws Exception {
        save("{\"accent\":\"teal\"}");
        for (String bad : List.of("{}", "{\"accent\":null}", "{\"accent\":\"\"}", "{\"accent\":\"Teal\"}", "{\"accent\":\"a b\"}", "{\"accent\":\"" + "x".repeat(33) + "\"}")) {
            JsonObject answer = save(bad);
            assertThat(answer.get("result").getAsString()).as(bad).isEqualTo("bad_accent");
            assertThat(answer.get("accent").getAsString()).as(bad).isEqualTo("teal");
        }
        assertThat(read().get("accent").getAsString()).isEqualTo("teal");
    }

    @Test
    void textThatIsNotJsonIsRefused() throws Exception {
        assertThat(mvc.perform(post("/api/appearance").contentType(MediaType.TEXT_PLAIN).content("indigo")).andReturn().getResponse().getStatus()).isEqualTo(415);
    }

    @Test
    void aSyncNeverCarriesTheAccentAndAReplaceNeverWipesIt() throws Exception {
        save("{\"accent\":\"crimson\"}");

        String snapshot = mvc.perform(get("/api/sync/snapshot")).andReturn().getResponse().getContentAsString();
        assertThat(snapshot).doesNotContain("crimson").doesNotContain("accent");

        SyncSnapshot empty = new SyncSnapshot(List.of(), List.of(), List.of(), List.of());
        MockHttpServletResponse replaced = mvc.perform(post("/api/sync/replace").contentType(MediaType.APPLICATION_JSON).content(gson.toJson(empty))).andReturn().getResponse();
        assertThat(replaced.getStatus()).isEqualTo(200);

        assertThat(read().get("accent").getAsString()).isEqualTo("crimson");
    }
}
