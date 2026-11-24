package io.github.b077as.smartjp.service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.github.b077as.jolt.spring.JoltRuntime;
import io.github.b077as.jpack.Jpack;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Desktop app: tells the jpack stub that the main window is on screen. The launcher started this app next to itself
 * ({@code Jpack.startNow}) and stays open until it hears this, so there is no moment with nothing on screen between the two.
 * Said once per run. Without a stub (an IDE run, {@code java -jar}) nothing is said and nothing else changes.
 * After that it also corrects the Windows "Apps and features" version (AppsFeatures), which needs the same stub.
 */
@Slf4j
@Component
@Profile("desktop")
@RequiredArgsConstructor
public class OnScreenNotifier {

    private static final AtomicBoolean SAID = new AtomicBoolean();

    private final JoltRuntime runtime;

    @PostConstruct
    void whenTheWindowIsUp() {
        if (!Jpack.isAvailable()) {
            return;
        }
        runtime.whenShown().thenRun(() -> Thread.ofVirtual().name("jpack-shown").start(this::tellTheStub));
    }

    private void tellTheStub() {
        if (SAID.compareAndSet(false, true)) {
            try (Jpack jpack = Jpack.connect()) {
                jpack.notifyShown().get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                // The launcher closes on its own after a while; nothing the user needs to see.
                log.warn("Could not tell the jpack stub that the window is on screen: {}", e.toString());
            }
        }
        AppsFeatures.syncVersion();
    }
}
