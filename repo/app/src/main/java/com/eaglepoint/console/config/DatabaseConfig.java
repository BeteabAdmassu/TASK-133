package com.eaglepoint.console.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.File;

public class DatabaseConfig {
    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);
    private static DatabaseConfig instance;
    private HikariDataSource dataSource;

    private DatabaseConfig() {}

    public static synchronized DatabaseConfig getInstance() {
        if (instance == null) instance = new DatabaseConfig();
        return instance;
    }

    public static void init() {
        getInstance().getDataSource();
    }

    public synchronized DataSource getDataSource() {
        if (dataSource == null) {
            String dbPath = AppConfig.getInstance().getDbPath();
            boolean inMemory = ":memory:".equals(dbPath);

            if (!inMemory) {
                // Ensure parent directory exists for file-backed databases
                File dbFile = new File(dbPath);
                if (dbFile.getParentFile() != null) {
                    dbFile.getParentFile().mkdirs();
                }
            }

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:sqlite:" + dbPath);
            config.setDriverClassName("org.sqlite.JDBC");
            if (inMemory) {
                // SQLite :memory: is per-connection — if we let the pool open
                // multiple connections, each one sees an empty, untouched DB.
                // Pinning the pool to a single persistent connection keeps the
                // migrated schema and seeded rows visible to every query.
                config.setMaximumPoolSize(1);
                config.setMinimumIdle(1);
                config.setConnectionInitSql("PRAGMA foreign_keys=ON;");
            } else {
                config.setMaximumPoolSize(5);
                config.setConnectionInitSql(
                    "PRAGMA journal_mode=WAL; PRAGMA foreign_keys=ON;"
                );
            }
            config.setConnectionTimeout(30000);
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);

            dataSource = new HikariDataSource(config);

            runMigrations();
        }
        return dataSource;
    }

    private void runMigrations() {
        log.info("Running Flyway migrations");
        Flyway flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migrations")
            .baselineOnMigrate(true)
            .load();
        flyway.migrate();
        log.info("Flyway migrations completed");
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
