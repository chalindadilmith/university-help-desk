package com.helpdesk.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * ONE-OFF CLEAN SLATE. Only runs when {@code app.reset-database=true} (environment variable
 * {@code RESET_DATABASE=true}); by default it does nothing.
 *
 * It deletes every row from every table (students, tickets, replies, attachments, notifications,
 * audit log, lost &amp; found, registrations, ... everything), restarts the id counters, and deletes
 * the uploaded files. Table definitions are left alone. Afterwards the normal start-up seeding runs
 * and recreates only the two sign-in accounts (admin and staff) plus the baseline ticket
 * categories / work-pool / FAQ / semester set-up, so the site is usable straight away.
 *
 * Run it once, then start the app again WITHOUT RESET_DATABASE, otherwise the data is wiped on every start.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE) // before RegistrationSchemaInitializer and DemoDataSeeder
@ConditionalOnProperty(name = "app.reset-database", havingValue = "true")
public class DatabaseResetRunner implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final String uploadDir;

    public DatabaseResetRunner(JdbcTemplate jdbc, @Value("${app.upload-dir:uploads}") String uploadDir) {
        this.jdbc = jdbc;
        this.uploadDir = uploadDir;
    }

    private record TableInfo(String name, boolean hasIdentity) { }

    @Override
    public void run(ApplicationArguments args) {
        log.warn("==========================================================================");
        log.warn(" app.reset-database=true  ->  DELETING ALL DATA from the database now.");
        log.warn(" Start the app again WITHOUT RESET_DATABASE once this has finished.");
        log.warn("==========================================================================");

        List<TableInfo> tables = jdbc.query(
                "SELECT QUOTENAME(s.name) + '.' + QUOTENAME(t.name) AS full_name, "
                        + "OBJECTPROPERTY(t.object_id, 'TableHasIdentity') AS has_identity "
                        + "FROM sys.tables t JOIN sys.schemas s ON s.schema_id = t.schema_id "
                        + "WHERE t.is_ms_shipped = 0",
                (rs, i) -> new TableInfo(rs.getString("full_name"), rs.getInt("has_identity") == 1));

        // foreign keys off while emptying, so the order of tables does not matter
        for (TableInfo t : tables) {
            jdbc.execute("ALTER TABLE " + t.name() + " NOCHECK CONSTRAINT ALL");
        }
        for (TableInfo t : tables) {
            jdbc.execute("DELETE FROM " + t.name());
        }
        for (TableInfo t : tables) {
            if (t.hasIdentity()) {
                jdbc.execute("DBCC CHECKIDENT ('" + t.name() + "', RESEED, 0)");
            }
        }
        for (TableInfo t : tables) {
            jdbc.execute("ALTER TABLE " + t.name() + " WITH CHECK CHECK CONSTRAINT ALL");
        }
        log.warn("Emptied {} tables.", tables.size());

        clearUploads();
    }

    /** Removes uploaded ticket attachments and lost &amp; found proof photos. */
    private void clearUploads() {
        for (String folder : List.of("ticket-attachments", "lost-found-proofs")) {
            Path dir = Paths.get(uploadDir, folder).toAbsolutePath().normalize();
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(dir)) {
                files.filter(Files::isRegularFile).sorted(Comparator.reverseOrder()).forEach(f -> {
                    try {
                        Files.deleteIfExists(f);
                    } catch (IOException e) {
                        log.warn("Could not delete uploaded file {}: {}", f, e.getMessage());
                    }
                });
            } catch (IOException e) {
                log.warn("Could not clear {}: {}", dir, e.getMessage());
            }
        }
        log.warn("Uploaded files cleared.");
    }
}
