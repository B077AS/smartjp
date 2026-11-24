package io.github.b077as.smartjp.launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Entry point of the launcher: the small window that runs before the SmartJP desktop app.
 *
 * <p>The jpack stub that started it (SmartJP.exe / the AppImage) owns the installation. The launcher asks the stub for updates, shows
 * the progress, and hands over to the app entry (see {@link UpdateManager}). It uses the same per-user data folder as the app, so the
 * logs are in one place. It has no logger of its own here: the log folder is set up first, and only then is logback started.
 */
public class Boot {

    /** When the JVM started running this class, for the start-up timing in the log. */
    static final long STARTED = System.currentTimeMillis();

    private Boot() {
    }

    /**
     * The folder the app uses too (SmartJpApplication.userDataFolder): {@code %LOCALAPPDATA%\smartjp} on Windows, {@code $XDG_DATA_HOME/smartjp}
     * or {@code ~/.local/share/smartjp} on Linux. {@code SMARTJP_DATA_DIR} or {@code -Dsmartjp.data-dir=<folder>} replaces it, as for the app.
     */
    public static Path dataDirectory() {
        String override = System.getProperty("smartjp.data-dir", "");
        if (override.isBlank()) {
            override = System.getenv().getOrDefault("SMARTJP_DATA_DIR", "");
        }
        if (!override.isBlank()) {
            return Paths.get(override);
        }
        String home = System.getProperty("user.home");
        Path base;
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            String local = System.getenv("LOCALAPPDATA");
            base = local != null && !local.isBlank() ? Paths.get(local) : Paths.get(home, "AppData", "Local");
        } else {
            String xdg = System.getenv("XDG_DATA_HOME");
            base = xdg != null && !xdg.isBlank() ? Paths.get(xdg) : Paths.get(home, ".local", "share");
        }
        return base.resolve("smartjp");
    }

    public static void main(String[] args) {
        System.setProperty("slf4j.provider", "ch.qos.logback.classic.spi.LogbackServiceProvider");
        Path logs = dataDirectory().resolve("logs");
        System.setProperty("smartjp.launcher.logDir", logs.toAbsolutePath().toString());
        try {
            Files.createDirectories(logs);
        } catch (IOException ignored) {
            // The launcher still works without a log file.
        }
        LauncherApp.run(Boolean.getBoolean("launcher.demo"));
    }
}
