package io.github.b077as.smartjp.rpc;

import java.util.concurrent.ExecutionException;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.github.b077as.jolt.Application;
import io.github.b077as.jolt.JoltRpc;
import io.github.b077as.smartjp.model.dto.response.ClipboardText;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Desktop app: reads the system clipboard for the Paste item of the page's own right-click menu ({@code jolt.call("clipboard.readText")}).
 * The web view does not let a page read the clipboard, so Java does it with Jolt's clipboard API. Plain text only. Copy and Cut need
 * nothing from here: the page does them itself. The website has no such call: a browser shows its own context menu.
 */
@Slf4j
@Component
@Profile("desktop")
@JoltRpc("clipboard")
@RequiredArgsConstructor
public class ClipboardRpc {

    private final Application application;

    /** Runs on a virtual thread, so waiting for the answer is fine. A failure reaches the page as an error, which then says to use Ctrl+V. */
    @JoltRpc
    public ClipboardText readText() throws InterruptedException {
        try {
            return new ClipboardText(application.clipboard().readText().get().orElse(""));
        } catch (ExecutionException e) {
            log.warn("The clipboard could not be read: {}", e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
            throw new IllegalStateException("The clipboard could not be read.", e);
        }
    }
}
