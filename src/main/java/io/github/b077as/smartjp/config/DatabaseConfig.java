package io.github.b077as.smartjp.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.zaxxer.hikari.HikariDataSource;

/**
 * The database: one SQLite file, {@code smartjp.db}, in the folder {@code smartjp.data-dir}. The website and the desktop
 * app run this same code and each has its own file (the website's folder is "data" next to where it was started, the
 * desktop app's is set in SmartJpApplication). Hibernate creates the tables at start (spring.jpa.hibernate.ddl-auto=update).
 * <p>
 * The pool has ONE connection on purpose: SQLite lets one writer in at a time, and one connection makes every call
 * wait its turn instead of failing with "database is locked".
 */
@Configuration
public class DatabaseConfig {

    public static final String FILE = "smartjp.db";

    @Bean
    public DataSource dataSource(@Value("${smartjp.data-dir}") String dataDir) {
        Path folder = Path.of(dataDir).toAbsolutePath();
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create the data folder " + folder, e);
        }
        HikariDataSource source = new HikariDataSource();
        // Forward slashes also on Windows: the SQLite driver reads the path as it is.
        source.setJdbcUrl("jdbc:sqlite:" + folder.resolve(FILE).toString().replace('\\', '/'));
        source.setMaximumPoolSize(1);
        source.setPoolName("smartjp-sqlite");
        return source;
    }
}
