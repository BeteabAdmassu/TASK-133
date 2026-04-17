package com.eaglepoint.console.scheduler.jobs;

import com.eaglepoint.console.config.DatabaseConfig;
import com.eaglepoint.console.service.NotificationService;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ArchivalJob implements Job {
    private static final Logger log = LoggerFactory.getLogger(ArchivalJob.class);

    @Override
    public void execute(JobExecutionContext context) {
        log.info("ArchivalJob starting");
        DataSource ds = DatabaseConfig.getInstance().getDataSource();

        String cutoffDate = LocalDate.now().minusMonths(24).format(DateTimeFormatter.ISO_LOCAL_DATE);
        int archivedCycles = 0;
        int archivedScorecards = 0;

        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Find cycles to archive
                List<Long> cycleIds = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id FROM evaluation_cycles WHERE status = 'CLOSED' AND end_date < ?")) {
                    ps.setString(1, cutoffDate);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) cycleIds.add(rs.getLong("id"));
                    }
                }

                for (Long cycleId : cycleIds) {
                    // Archive cycle
                    conn.prepareStatement(
                        "INSERT OR IGNORE INTO evaluation_cycles_archive SELECT *, datetime('now') as archived_at FROM evaluation_cycles WHERE id = " + cycleId
                    ).executeUpdate();

                    // Archive scorecards
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT OR IGNORE INTO scorecards_archive SELECT *, datetime('now') as archived_at FROM scorecards WHERE cycle_id = ?")) {
                        ps.setLong(1, cycleId);
                        int count = ps.executeUpdate();
                        archivedScorecards += count;
                    }

                    // Mark as archived
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE evaluation_cycles SET status = 'ARCHIVED', updated_at = datetime('now') WHERE id = ?")) {
                        ps.setLong(1, cycleId);
                        ps.executeUpdate();
                    }
                    archivedCycles++;
                }

                conn.commit();
                log.info("ArchivalJob completed: {} cycles, {} scorecards archived", archivedCycles, archivedScorecards);
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            log.error("ArchivalJob failed: {}", e.getMessage(), e);
            NotificationService.getInstance().addAlert("ERROR",
                "Monthly archival failed: " + e.getMessage(), "Archive", null);
        }
    }
}
