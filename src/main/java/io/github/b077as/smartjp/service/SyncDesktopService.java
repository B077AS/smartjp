package io.github.b077as.smartjp.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import io.github.b077as.jolt.credentials.CredentialStoreException;
import io.github.b077as.smartjp.model.dto.request.ConnectRequest;
import io.github.b077as.smartjp.model.dto.request.RunSyncRequest;
import io.github.b077as.smartjp.model.dto.response.CompareResponse;
import io.github.b077as.smartjp.model.dto.response.ConnectResponse;
import io.github.b077as.smartjp.model.dto.response.RunSyncResponse;
import io.github.b077as.smartjp.model.dto.summary.SyncCounts;
import io.github.b077as.smartjp.model.dto.summary.SyncStatus;
import io.github.b077as.smartjp.model.dto.summary.SyncSummary;
import io.github.b077as.smartjp.model.dto.sync.SyncOutcome;
import io.github.b077as.smartjp.model.dto.sync.SyncSnapshot;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The desktop app's side of a sync (desktop only: the website is the other side and never calls out). It remembers one server:
 * the address in the database, the access key in the operating system's keystore (never in the database or a log). Every sync
 * is started here, with one of three actions:
 * <ul>
 *   <li><b>merge</b> (the default, loses nothing): each side takes in what it is missing;</li>
 *   <li><b>pull</b>: this desktop becomes a copy of the server (this desktop is saved to a backup file first);</li>
 *   <li><b>push</b>: the server becomes a copy of this desktop (the server saves its own backup first).</li>
 * </ul>
 * Everything answers with a result word, never an exception, so the page only shows what came of it.
 */
@Slf4j
@Service
@Profile("desktop")
@RequiredArgsConstructor
public class SyncDesktopService {

    private static final String SERVER = "sync.server";
    private static final Set<String> ACTIONS = Set.of("merge", "pull", "push");

    private final SyncClient client;
    private final SyncService sync;
    private final AppSettingsService settings;
    private final SyncKeyVault vault;

    @Getter
    @AllArgsConstructor
    private static class Connection {
        private final String address;
        private final String key;
    }

    public SyncStatus status() {
        Optional<Connection> connection = connection();
        return new SyncStatus(connection.isPresent(), connection.map(Connection::getAddress).orElse(null));
    }

    /** Checks the address and the key against the server, and keeps them only if the server accepts them. */
    public ConnectResponse connect(ConnectRequest request) {
        String address = normalise(request.address());
        if (address == null) {
            return new ConnectResponse("bad_address", status());
        }
        String key = request.key() == null ? "" : request.key().strip();
        if (key.isEmpty()) {
            return new ConnectResponse("bad_key", status());
        }
        RemoteReply<SyncSummary> reply = client.get(address, key, "/api/sync/summary", SyncSummary.class);
        if (!reply.ok()) {
            return new ConnectResponse(failure(reply), status());
        }
        try {
            vault.put(key);
        } catch (CredentialStoreException e) { // also "unavailable": a Linux without a Secret Service
            log.warn("The access key could not be saved in the system keystore: {}", e.getMessage());
            return new ConnectResponse("no_keystore", status());
        }
        settings.put(SERVER, address);
        log.info("Connected to a sync server");
        return new ConnectResponse("connected", status());
    }

    public void disconnect() {
        settings.remove(SERVER);
        settings.remove(SyncStore.LAST_SYNC);
        try {
            vault.delete();
        } catch (CredentialStoreException e) {
            log.warn("The saved access key could not be removed from the system keystore: {}", e.getMessage());
        }
        log.info("Disconnected from the sync server");
    }

    /** This desktop and the server side by side, for the Sync screen. */
    public CompareResponse compare() {
        Optional<Connection> connection = connection();
        if (connection.isEmpty()) {
            return new CompareResponse("not_connected", null, null);
        }
        RemoteReply<SyncSummary> reply = client.get(connection.get().getAddress(), connection.get().getKey(), "/api/sync/summary", SyncSummary.class);
        if (!reply.ok()) {
            return new CompareResponse(failure(reply), sync.summary(), null); // this desktop is known even when the server is not
        }
        return new CompareResponse("ok", sync.summary(), reply.getBody());
    }

    public RunSyncResponse run(RunSyncRequest request) {
        String action = request.action();
        if (action == null || !ACTIONS.contains(action)) {
            return failed("bad_action");
        }
        Optional<Connection> connection = connection();
        if (connection.isEmpty()) {
            return failed("not_connected");
        }
        try {
            return switch (action) {
                case "merge" -> merge(connection.get());
                case "pull" -> pull(connection.get());
                default -> push(connection.get());
            };
        } catch (Exception e) {
            log.error("The sync failed: {}", e.getMessage(), e);
            return failed("failed");
        }
    }

    private RunSyncResponse merge(Connection server) {
        RemoteReply<SyncSnapshot> theirs = client.get(server.getAddress(), server.getKey(), "/api/sync/snapshot", SyncSnapshot.class);
        if (!theirs.ok()) {
            return failed(failure(theirs));
        }
        SyncSnapshot mine = sync.snapshot(); // taken before taking theirs in, so the server only gets what it may lack
        SyncOutcome received = sync.mergeIn(theirs.getBody());
        RemoteReply<SyncOutcome> sent = client.post(server.getAddress(), server.getKey(), "/api/sync/merge", mine, SyncOutcome.class);
        if (!sent.ok()) {
            return failed(failure(sent)); // this side is merged already; running it again finishes the job (merging twice changes nothing)
        }
        return new RunSyncResponse("done", null, null, counts(sent.getBody()), counts(received));
    }

    private RunSyncResponse pull(Connection server) {
        RemoteReply<SyncSnapshot> theirs = client.get(server.getAddress(), server.getKey(), "/api/sync/snapshot", SyncSnapshot.class);
        if (!theirs.ok()) {
            return failed(failure(theirs));
        }
        if (!SyncService.isComplete(theirs.getBody())) {
            return failed("failed"); // not what this server should answer: never wipe this desktop with it
        }
        SyncOutcome received = sync.replaceWith(theirs.getBody());
        return new RunSyncResponse("done", received.getBackup(), "desktop", counts(new SyncOutcome()), counts(received));
    }

    private RunSyncResponse push(Connection server) {
        RemoteReply<SyncOutcome> reply = client.post(server.getAddress(), server.getKey(), "/api/sync/replace", sync.snapshot(), SyncOutcome.class);
        if (!reply.ok()) {
            return failed(failure(reply));
        }
        sync.markSynced();
        return new RunSyncResponse("done", reply.getBody().getBackup(), "server", counts(reply.getBody()), counts(new SyncOutcome()));
    }

    private Optional<Connection> connection() {
        Optional<String> address = settings.get(SERVER);
        if (address.isEmpty()) {
            return Optional.empty();
        }
        try {
            return vault.get().map(key -> new Connection(address.get(), key));
        } catch (CredentialStoreException e) {
            log.warn("The access key could not be read from the system keystore: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static String failure(RemoteReply<?> reply) {
        return reply.getStatus() == 0 ? "unreachable" : reply.getStatus() == 401 ? "bad_key" : "failed";
    }

    private static RunSyncResponse failed(String result) {
        return new RunSyncResponse(result, null, null, null, null);
    }

    private static SyncCounts counts(SyncOutcome outcome) {
        return new SyncCounts(outcome.getAnswers(), outcome.getLists(), outcome.getCards(), outcome.getRemoved());
    }

    /** "my.server:8081" becomes "https://my.server:8081"; only http and https, a host, no user name in it, no trailing slash. Null when it cannot be an address. */
    public static String normalise(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.strip();
        if (!text.contains("://")) {
            text = "https://" + text;
        }
        try {
            URI uri = new URI(text);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if ((!scheme.equals("http") && !scheme.equals("https")) || uri.getHost() == null || uri.getUserInfo() != null) {
                return null;
            }
            String path = uri.getRawPath() == null ? "" : uri.getRawPath().replaceAll("/+$", "");
            return scheme + "://" + uri.getHost() + (uri.getPort() >= 0 ? ":" + uri.getPort() : "") + path;
        } catch (URISyntaxException e) {
            return null;
        }
    }
}
