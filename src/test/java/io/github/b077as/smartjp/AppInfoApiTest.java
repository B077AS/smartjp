package io.github.b077as.smartjp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.google.gson.Gson;

import io.github.b077as.smartjp.model.dto.summary.AppInfoSummary;

/** What the About tab asks for: the version of the build, the system and how it runs. */
@SpringBootTest
@AutoConfigureMockMvc
class AppInfoApiTest {

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
    private Gson gson;

    @Test
    void theWebsiteReportsItsVersionTheSystemAndThatItIsTheWebsite() throws Exception {
        MockHttpServletResponse response = mvc.perform(get("/api/app")).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        AppInfoSummary info = gson.fromJson(response.getContentAsString(), AppInfoSummary.class);

        assertThat(info.mode()).isEqualTo("website");
        assertThat(info.os()).isNotBlank();
        assertThat(info.java()).isNotBlank();
        // Maven fills the version in from the pom (smartjp.version=@project.version@); a raw placeholder would mean the filtering is off.
        assertThat(info.version()).isNotBlank().doesNotStartWith("@");
    }
}
