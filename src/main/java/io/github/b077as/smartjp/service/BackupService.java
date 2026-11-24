package io.github.b077as.smartjp.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Saves a copy of the database before a sync overwrites it ({@code VACUUM INTO}: SQLite writes a consistent copy of the live file,
 * which a plain file copy cannot promise). The copy sits next to the database as {@code backup-<date>-<time>.db}; nothing deletes
 * them. VACUUM cannot run inside a transaction, so callers must not hold one open (and with the pool's single connection they
 * could not get one anyway).
 */
@Slf4j
@Service
public class BackupService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss");

    private final DataSource dataSource;
    private final Clock clock;
    private final Path folder;

    public BackupService(DataSource dataSource, Clock clock, @Value("${smartjp.data-dir}") String dataDir) {
        this.dataSource = dataSource;
        this.clock = clock;
        this.folder = Path.of(dataDir).toAbsolutePath();
    }

    /** Makes the copy and returns its file name (not the path). */
    public String make() {
        String stamp = STAMP.format(Instant.now(clock).atZone(clock.getZone()));
        String name = "backup-" + stamp + ".db";
        for (int n = 2; Files.exists(folder.resolve(name)); n++) {
            name = "backup-" + stamp + "-" + n + ".db"; // two in the same second
        }
        // The SQL string takes the path in quotes; forward slashes also on Windows, a quote in the folder name doubled.
        String target = folder.resolve(name).toString().replace('\\', '/').replace("'", "''");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("VACUUM INTO '" + target + "'");
        } catch (SQLException e) {
            throw new UncheckedIOException(new IOException("The backup could not be made: " + e.getMessage(), e));
        }
        log.info("Saved a backup of the database as {}", name);
        return name;
    }
}
