package io.github.b077as.smartjp.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.zip.GZIPInputStream;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import io.github.b077as.smartjp.security.AccessKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The desktop app calling its server's /api/sync. Redirects are never followed: the access key travels in a header, and a
 * redirect would hand it to whatever address answers. Failures come back as a {@link RemoteReply} with status 0, not as exceptions.
 * Neither the key nor the address is ever logged above DEBUG.
 */
@Slf4j
@Component
@Profile("desktop")
@RequiredArgsConstructor
public class SyncClient {

    private final Gson gson;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();

    public <T> RemoteReply<T> get(String base, String key, String path, Class<T> type) {
        return send(request(base, key, path).GET().build(), type);
    }

    public <T> RemoteReply<T> post(String base, String key, String path, Object body, Class<T> type) {
        HttpRequest request = request(base, key, path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body), StandardCharsets.UTF_8))
                .build();
        return send(request, type);
    }

    private static HttpRequest.Builder request(String base, String key, String path) {
        return HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(120)) // a snapshot of a lot of answers is some megabytes
                .header(AccessKeyService.HEADER, key)
                .header("Accept", "application/json")
                .header("Accept-Encoding", "gzip");
    }

    private <T> RemoteReply<T> send(HttpRequest request, Class<T> type) {
        try {
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream raw = response.body()) {
                int status = response.statusCode();
                if (status != 200) {
                    return new RemoteReply<>(status, null);
                }
                boolean gzip = response.headers().firstValue("Content-Encoding").map(v -> v.equalsIgnoreCase("gzip")).orElse(false);
                try (InputStream in = gzip ? new GZIPInputStream(raw) : raw) {
                    String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    return new RemoteReply<>(status, gson.fromJson(text, type));
                } catch (JsonParseException e) {
                    log.debug("The server answered something that is not JSON: {}", e.getMessage());
                    return new RemoteReply<>(status, null); // 200 but not our server (a web page, for example)
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            log.debug("The server could not be reached: {}", e.getMessage());
            return new RemoteReply<>(0, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new RemoteReply<>(0, null);
        }
    }
}
