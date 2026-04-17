package com.eaglepoint.console.repository;

import com.eaglepoint.console.model.ScheduledJobConfig;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class ScheduledJobRepository extends BaseRepository {

    public ScheduledJobRepository(DataSource ds) {
        super(ds);
    }

    private ScheduledJobConfig mapRow(ResultSet rs) throws SQLException {
        ScheduledJobConfig j = new ScheduledJobConfig();
        j.setId(rs.getLong("id"));
        j.setJobType(rs.getString("job_type"));
        j.setCronExpression(rs.getString("cron_expression"));
        j.setTimeoutSeconds(rs.getInt("timeout_seconds"));
        j.setLastRun(rs.getString("last_run"));
        j.setNextRun(rs.getString("next_run"));
        j.setStatus(rs.getString("status"));
        j.setLastResult(rs.getString("last_result"));
        j.setConfigJson(rs.getString("config_json"));
        j.setCreatedAt(rs.getString("created_at"));
        j.setUpdatedAt(rs.getString("updated_at"));
        return j;
    }

    public List<ScheduledJobConfig> findAll() {
        return queryList("SELECT * FROM scheduled_jobs ORDER BY id", this::mapRow);
    }

    public Optional<ScheduledJobConfig> findById(long id) {
        return queryOne("SELECT * FROM scheduled_jobs WHERE id = ?", this::mapRow, id);
    }

    public Optional<ScheduledJobConfig> findByType(String jobType) {
        return queryOne("SELECT * FROM scheduled_jobs WHERE job_type = ? ORDER BY id LIMIT 1",
            this::mapRow, jobType);
    }

    public void updateStatus(long id, String status, String lastRun, String nextRun, String lastResult) {
        execute(
            "UPDATE scheduled_jobs SET status=?, last_run=?, next_run=?, last_result=?, updated_at=datetime('now') WHERE id=?",
            status, lastRun, nextRun, lastResult, id
        );
    }

    public void pauseJob(long id) {
        execute("UPDATE scheduled_jobs SET status='PAUSED', updated_at=datetime('now') WHERE id=?", id);
    }

    public void resumeJob(long id) {
        execute("UPDATE scheduled_jobs SET status='ACTIVE', updated_at=datetime('now') WHERE id=?", id);
    }

    public long insert(ScheduledJobConfig j) {
        return insertAndGetId(
            "INSERT INTO scheduled_jobs (job_type, cron_expression, timeout_seconds, status, config_json) " +
            "VALUES (?,?,?,?,?)",
            j.getJobType(), j.getCronExpression(),
            j.getTimeoutSeconds() > 0 ? j.getTimeoutSeconds() : 3600,
            j.getStatus() != null ? j.getStatus() : "ACTIVE",
            j.getConfigJson()
        );
    }

    public void update(ScheduledJobConfig j) {
        execute(
            "UPDATE scheduled_jobs SET cron_expression=?, timeout_seconds=?, status=?, config_json=?, updated_at=datetime('now') WHERE id=?",
            j.getCronExpression(), j.getTimeoutSeconds(), j.getStatus(),
            j.getConfigJson(), j.getId()
        );
    }

    public void delete(long id) {
        execute("DELETE FROM scheduled_jobs WHERE id = ?", id);
    }
}
