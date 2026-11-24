package io.github.b077as.smartjp.rpc;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.github.b077as.jolt.JoltRpc;
import io.github.b077as.smartjp.model.dto.request.ConnectRequest;
import io.github.b077as.smartjp.model.dto.request.RunSyncRequest;
import io.github.b077as.smartjp.model.dto.response.CompareResponse;
import io.github.b077as.smartjp.model.dto.response.ConnectResponse;
import io.github.b077as.smartjp.model.dto.response.RunSyncResponse;
import io.github.b077as.smartjp.model.dto.summary.SyncStatus;
import io.github.b077as.smartjp.service.SyncDesktopService;
import lombok.RequiredArgsConstructor;

/**
 * Desktop app: the Sync screen's door, as {@code jolt.call("sync.status")}, {@code "sync.connect"}, {@code "sync.disconnect"},
 * {@code "sync.compare"} and {@code "sync.run"}. The website has no twin: it is the other side of a sync, and its own door
 * is SyncController.
 */
@Component
@Profile("desktop")
@JoltRpc("sync")
@RequiredArgsConstructor
public class SyncRpc {

    private final SyncDesktopService syncDesktopService;

    @JoltRpc
    public SyncStatus status() {
        return syncDesktopService.status();
    }

    @JoltRpc
    public ConnectResponse connect(ConnectRequest request) {
        return syncDesktopService.connect(request);
    }

    @JoltRpc
    public void disconnect() {
        syncDesktopService.disconnect();
    }

    @JoltRpc
    public CompareResponse compare() {
        return syncDesktopService.compare();
    }

    @JoltRpc
    public RunSyncResponse run(RunSyncRequest request) {
        return syncDesktopService.run(request);
    }
}
