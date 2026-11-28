package io.github.b077as.smartjp;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.github.b077as.jolt.CrashEvent;
import io.github.b077as.jolt.WebWindow;
import io.github.b077as.jolt.spring.JoltSpringApplication;

/**
 * The learning app. One jar, two ways to start it:
 * <ul>
 *   <li><b>website</b>, {@code java -jar smartjp.jar}: a normal Spring MVC application. It serves the Vue app and its
 *       small {@code /api}. Meant to be hosted by the user themselves (it listens on 127.0.0.1 unless told otherwise).</li>
 *   <li><b>desktop</b>, {@code java -jar smartjp.jar --desktop}: the same application started through Jolt, in a
 *       window, with no web server at all. The page's files are served inside the window and it reaches Java through
 *       {@code @JoltRpc} beans (profile "desktop", application-desktop.properties).</li>
 * </ul>
 * Both keep their data in a SQLite file (DatabaseConfig) and run the same services; only the door the page uses differs
 * (HTTP controllers in the website, @JoltRpc beans in the window).
 * Either way the Vue app starts with it, from one place: this module's resources (static/). {@code npm run build} in
 * frontend/ writes it there (git-ignored), Maven copies it into target/classes on every build, and {@code mvn package}
 * builds it first and packs it into the jar. An IDE run and the jar therefore read the same pages, with no flag and no
 * second copy that can go stale. After changing the front end: {@code npm run build}, then restart.
 */
@SpringBootApplication
public class SmartJpApplication {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720; // the window size it returns to when restored
    private static final int PAPER = 0xF7F7F5; // the page's background (Snow), so there is no white flash while it loads

    public static void main(String[] args) {
        List<String> flags = Arrays.asList(args);
        if (SmartJpApplication.class.getResource("/static/index.html") == null) {
            throw new IllegalStateException("There are no pages in the application. Build the front end first: "
                    + "\"cd frontend\", \"npm install\", \"npm run build\" (it writes into client/src/main/resources/static), "
                    + "then start again. \"mvn package\" does this for the jar.");
        }
        if (flags.contains("--desktop")) {
            desktop(args);
        } else {
            SpringApplication.run(SmartJpApplication.class, args);
        }
    }

    private static void desktop(String[] args) {
        // Where Jolt keeps this app's data (the window's browser storage lives there): a folder of its own.
        System.setProperty("jolt.app-id", System.getProperty("jolt.app-id", "smartjp"));
        // The database lives in the same per-user folder, unless the user chose another place (property or environment variable).
        if (System.getProperty("smartjp.data-dir") == null && System.getenv("SMARTJP_DATA_DIR") == null) {
            System.setProperty("smartjp.data-dir", userDataFolder().toString());
        }
        // The log goes into the data folder too (logback.xml): an installed copy starts in its install folder, which is not the place for it.
        // Set before anything logs, since logback reads it when it starts.
        if (System.getProperty("smartjp.log-dir") == null) {
            String data = System.getProperty("smartjp.data-dir");
            if (data == null) {
                data = System.getenv("SMARTJP_DATA_DIR");
            }
            System.setProperty("smartjp.log-dir", Path.of(data).resolve("logs").toString());
        }
        new JoltSpringApplication(SmartJpApplication.class)
                .spring(spring -> spring.setAdditionalProfiles("desktop"))
                .window(options -> options
                        .withTitle("SmartJP")
                        .withSize(WIDTH, HEIGHT)
                        .withMinSize(WIDTH, HEIGHT) // the window cannot be made smaller than its normal size
                        .withCentered(true) // where a restored window sits (Jolt's default too; ignored while maximized)
                        .withMaximized(true) // WIDTH x HEIGHT is only the size the window returns to when restored
                        .withIcon(readIcon())
                        .withBackgroundColor(PAPER))
                .onWindow(SmartJpApplication::recoverFromCrashes)
                .run(args);
    }

    /**
     * The page holds nothing that is not in the database (but the round being played), so a crashed one is simply loaded
     * again. If the whole web view is gone the window cannot recover, so it is closed, which ends the application, rather
     * than left blank. Jolt logs the crash.
     */
    private static void recoverFromCrashes(WebWindow window) {
        window.setReloadOnCrash(true);
        window.onCrash(event -> {
            if (event.reason() == CrashEvent.Reason.WEB_VIEW_LOST) {
                window.close();
            }
        });
    }

    /** This user's app data folder: %LOCALAPPDATA%\smartjp on Windows (where Jolt keeps the window's own data), $XDG_DATA_HOME/smartjp or ~/.local/share/smartjp on Linux. */
    private static Path userDataFolder() {
        Path base;
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            String local = System.getenv("LOCALAPPDATA");
            base = local != null && !local.isBlank() ? Path.of(local) : Path.of(System.getProperty("user.home"), "AppData", "Local");
        } else {
            String xdg = System.getenv("XDG_DATA_HOME");
            base = xdg != null && !xdg.isBlank() ? Path.of(xdg) : Path.of(System.getProperty("user.home"), ".local", "share");
        }
        return base.resolve("smartjp");
    }

    /** The window and taskbar icon: one .ico with the logo drawn at 16 to 256 px, so Windows never has to scale it. Fixed colours, it does not follow the accent. */
    private static byte[] readIcon() {
        try (InputStream in = SmartJpApplication.class.getResourceAsStream("/icon.ico")) {
            return Objects.requireNonNull(in, "icon.ico is missing from the resources").readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
