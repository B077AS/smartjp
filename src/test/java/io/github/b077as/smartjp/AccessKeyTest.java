package io.github.b077as.smartjp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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

import io.github.b077as.smartjp.security.AccessKeyService;
import jakarta.servlet.http.Cookie;

/**
 * The website's access key: /api is refused without it, a browser trades the key for a cookie once, a new key cuts the old one off.
 * Every request here sets the key header itself (an empty one for "no key"), so the test does not depend on the default one.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccessKeyTest {

    @DynamicPropertySource
    static void dataFolder(DynamicPropertyRegistry registry) throws IOException {
        Path folder = Files.createTempDirectory("smartjp-key-test-");
        registry.add("smartjp.data-dir", folder::toString);
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private AccessKeyService keys;
    @Autowired
    private Gson gson;

    private MockHttpServletResponse listsWith(String header, Cookie cookie) throws Exception {
        var request = get("/api/lists").header(AccessKeyService.HEADER, header);
        if (cookie != null) {
            request = request.cookie(cookie);
        }
        return mvc.perform(request).andReturn().getResponse();
    }

    private MockHttpServletResponse unlock(String key) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("key", key);
        return mvc.perform(post("/api/session").header(AccessKeyService.HEADER, "").contentType(MediaType.APPLICATION_JSON).content(body.toString())).andReturn().getResponse();
    }

    /** The cookie a browser would store from a Set-Cookie line. */
    private static Cookie cookieFrom(MockHttpServletResponse response) {
        String line = response.getHeader("Set-Cookie");
        assertThat(line).isNotNull();
        String[] pair = line.split(";")[0].split("=", 2);
        return new Cookie(pair[0], pair[1]);
    }

    @Test
    void theKeyIsRandomAndLongEnoughToBeUnguessable() {
        assertThat(keys.key()).hasSizeGreaterThanOrEqualTo(43); // 32 random bytes, base64url without padding
    }

    @Test
    void apiAnswersAreRefusedWithoutTheKeyAndStillCarryTheSecurityHeaders() throws Exception {
        MockHttpServletResponse none = listsWith("", null);
        assertThat(none.getStatus()).isEqualTo(401);
        assertThat(gson.fromJson(none.getContentAsString(), JsonObject.class).get("code").getAsString()).isEqualTo("locked");
        assertThat(none.getHeader("Content-Security-Policy")).contains("default-src 'self'");
        assertThat(none.getHeader("Cache-Control")).isEqualTo("no-store");

        assertThat(listsWith("not-the-key", null).getStatus()).isEqualTo(401);
        assertThat(listsWith(keys.key() + "x", null).getStatus()).isEqualTo(401);
        assertThat(listsWith(keys.key(), null).getStatus()).isEqualTo(200);
    }

    @Test
    void everyApiAddressIsGuardedNotOnlyTheListed() throws Exception {
        // an address that does not exist is refused too (the 401 comes before the 404), so nothing can be probed without the key
        assertThat(mvc.perform(get("/api/nothing-here").header(AccessKeyService.HEADER, "")).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mvc.perform(get("/api/sync/snapshot").header(AccessKeyService.HEADER, "")).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mvc.perform(get("/api/sync/key").header(AccessKeyService.HEADER, "")).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void thePagesThemselvesStayPublic() throws Exception {
        assertThat(mvc.perform(get("/play").header(AccessKeyService.HEADER, "")).andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void aBrowserTradesTheKeyForACookieOnce() throws Exception {
        assertThat(unlock("wrong").getStatus()).isEqualTo(401);
        assertThat(unlock(null).getStatus()).isEqualTo(401);

        MockHttpServletResponse ok = unlock("  " + keys.key() + "  "); // pasted keys often carry spaces
        assertThat(ok.getStatus()).isEqualTo(200);
        String line = ok.getHeader("Set-Cookie");
        assertThat(line).contains("HttpOnly").contains("SameSite=Strict").doesNotContain(keys.key()); // the cookie never holds the key itself

        Cookie cookie = cookieFrom(ok);
        assertThat(listsWith("", cookie).getStatus()).isEqualTo(200);
        assertThat(listsWith("", new Cookie(cookie.getName(), "forged")).getStatus()).isEqualTo(401);

        String status = mvc.perform(get("/api/session").header(AccessKeyService.HEADER, "").cookie(cookie)).andReturn().getResponse().getContentAsString();
        assertThat(gson.fromJson(status, JsonObject.class).get("unlocked").getAsBoolean()).isTrue();
        String locked = mvc.perform(get("/api/session").header(AccessKeyService.HEADER, "")).andReturn().getResponse().getContentAsString();
        assertThat(gson.fromJson(locked, JsonObject.class).get("unlocked").getAsBoolean()).isFalse();
    }

    @Test
    void aNewKeyCutsTheOldOneOffAtOnce() throws Exception {
        Cookie oldCookie = cookieFrom(unlock(keys.key()));
        String oldKey = keys.key();

        MockHttpServletResponse renewed = mvc.perform(post("/api/sync/key/renew").header(AccessKeyService.HEADER, oldKey)).andReturn().getResponse();
        assertThat(renewed.getStatus()).isEqualTo(200);
        String newKey = gson.fromJson(renewed.getContentAsString(), JsonObject.class).get("key").getAsString();
        assertThat(newKey).isNotEqualTo(oldKey).isEqualTo(keys.key());

        assertThat(listsWith(oldKey, null).getStatus()).isEqualTo(401);
        assertThat(listsWith("", oldCookie).getStatus()).isEqualTo(401);
        assertThat(listsWith(newKey, null).getStatus()).isEqualTo(200);
        assertThat(listsWith("", cookieFrom(renewed)).getStatus()).isEqualTo(200); // the browser that renewed it keeps working
    }
}
