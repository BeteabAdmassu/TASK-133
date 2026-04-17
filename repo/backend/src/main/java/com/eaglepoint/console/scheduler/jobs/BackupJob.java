package com.eaglepoint.console.scheduler.jobs;

import com.eaglepoint.console.config.AppConfig;
import com.eaglepoint.console.service.NotificationService;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public class BackupJob implements Job {
    private static final Logger log = LoggerFactory.getLogger(BackupJob.class);

    @Override
    public void execute(JobExecutionContext context) {
        log.info("BackupJob starting");
        AppConfig config = AppConfig.getInstance();
        String dbPath = config.getDbPath();
        String backupDir = config.getBackupDir();

        try {
            Path source = Paths.get(dbPath);
            if (!Files.exists(source)) {
                log.warn("Database file not found: {}", dbPath);
                return;
            }

            Path backupDirPath = Paths.get(backupDir);
            Files.createDirectories(backupDirPath);

            String timestamp = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            Path dest = backupDirPath.resolve("console_backup_" + timestamp + ".db");
            Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
            log.info("Backup created: {}", dest);

            // Delete backups older than 14 days
            deleteOldBackups(backupDirPath, 14);

            NotificationService.getInstance().addAlert("INFO",
                "Nightly backup completed successfully: " + dest.getFileName(),
                "Backup", null);
        } catch (Exception e) {
            log.error("BackupJob failed: {}", e.getMessage(), e);
            NotificationService.getInstance().addAlert("ERROR",
                "Nightly backup failed: " + e.getMessage(), "Backup", null);
        }
    }

    private void deleteOldBackups(Path backupDir, int retentionDays) throws IOException {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        Files.walkFileTree(backupDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.getFileName().toString().startsWith("console_backup_")) {
                    if (attrs.lastModifiedTime().toInstant().isBefore(cutoff)) {
                        Files.delete(file);
                        log.info("Deleted old backup: {}", file);
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
