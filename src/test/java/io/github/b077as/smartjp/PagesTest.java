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

/** Website mode: how the built Vue app is served (src/test/resources/static/index.html stands in for the real build). */
@SpringBootTest
@AutoConfigureMockMvc
class PagesTest {

    @DynamicPropertySource
    static void dataFolder(DynamicPropertyRegistry registry) throws IOException {
        Path folder = Files.createTempDirectory("smartjp-pages-test-");
        registry.add("smartjp.data-dir", folder::toString);
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void anyPageAddressServesTheAppWithoutAListOfPages() throws Exception {
        // No list of pages exists on the server: the router in the page knows them. Any address that is not a file is the app.
        for (String path : new String[] {"/play", "/settings", "/a-page-nobody-listed", "/deep/in/the/router"}) {
            MockHttpServletResponse page = mvc.perform(get(path)).andReturn().getResponse();
            assertThat(page.getStatus()).as(path).isEqualTo(200);
            assertThat(page.getContentAsString()).as(path).contains("smartjp test page");
        }
        // The site root is forwarded to the front page (MockMvc records the forward without following it).
        assertThat(mvc.perform(get("/")).andReturn().getResponse().getForwardedUrl()).isEqualTo("/index.html");
    }

    @Test
    void aMissingFileIsA404NotAPage() throws Exception {
        // The browser asked for a script or a font: giving it HTML would only produce a confusing error.
        for (String path : new String[] {"/missing.js", "/assets/missing.js", "/deep/missing.css"}) {
            assertThat(mvc.perform(get(path)).andReturn().getResponse().getStatus()).as(path).isEqualTo(404);
        }
    }

    @Test
    void anUnknownApiAddressIsA404NotThePage() throws Exception {
        assertThat(mvc.perform(get("/api/nothing-here")).andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void everythingCarriesTheSecurityHeadersAndApiAnswersAreNeverCached() throws Exception {
        MockHttpServletResponse page = mvc.perform(get("/play")).andReturn().getResponse();
        assertThat(page.getHeader("Content-Security-Policy")).contains("default-src 'self'").contains("frame-ancestors 'none'");
        assertThat(page.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(page.getHeader("Cache-Control")).isEqualTo("no-cache");

        MockHttpServletResponse api = mvc.perform(get("/api/lists")).andReturn().getResponse();
        assertThat(api.getHeader("Content-Security-Policy")).contains("default-src 'self'");
        assertThat(api.getHeader("Cache-Control")).isEqualTo("no-store");
    }
}
