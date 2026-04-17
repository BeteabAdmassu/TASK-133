package com.eaglepoint.console.repository;

import com.eaglepoint.console.model.ExportJob;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class ExportJobRepository extends BaseRepository {

    public ExportJobRepository(DataSource ds) {
        super(ds);
    }

    private ExportJob mapRow(ResultSet rs) throws SQLException {
        ExportJob j = new ExportJob();
        j.setId(rs.getLong("id"));
        j.setType(rs.getString("type"));
        j.setEntityType(rs.getString("entity_type"));
        j.setFiltersJson(rs.getString("filters_json"));
        j.setDestinationPath(rs.getString("destination_path"));
        j.setStatus(rs.getString("status"));
        j.setOutputFilePath(rs.getString("output_file_path"));
        j.setSha256Hash(rs.getString("sha256_hash"));
        j.setInitiatedBy(rs.getLong("initiated_by"));
        j.setStartedAt(rs.getString("started_at"));
        j.setCompletedAt(rs.getString("completed_at"));
        j.setErrorMessage(rs.getString("error_message"));
        j.setCheckpointPath(rs.getString("checkpoint_path"));
        j.setCreatedAt(rs.getString("created_at"));
        j.setUpdatedAt(rs.getString("updated_at"));
        return j;
    }

    public Optional<ExportJob> findById(long id) {
        return queryOne("SELECT * FROM export_jobs WHERE id = ?", this::mapRow, id);
    }

    public long insert(ExportJob j) {
        return insertAndGetId(
            "INSERT INTO export_jobs (type, entity_type, filters_json, destination_path, status, initiated_by) VALUES (?,?,?,?,'PENDING',?)",
            j.getType(), j.getEntityType(), j.getFiltersJson(), j.getDestinationPath(), j.getInitiatedBy()
        );
    }

    public void updateStatus(long id, String status, String outputPath, String sha256, String errorMsg) {
        execute(
            "UPDATE export_jobs SET status=?, output_file_path=?, sha256_hash=?, error_message=?, completed_at=datetime('now'), updated_at=datetime('now') WHERE id=?",
            status, outputPath, sha256, errorMsg, id
        );
    }

    public void updateStarted(long id) {
        execute(
            "UPDATE export_jobs SET status='RUNNING', started_at=datetime('now'), updated_at=datetime('now') WHERE id=?",
            id
        );
    }

    public List<ExportJob> findIncomplete() {
        return queryList(
            "SELECT * FROM export_jobs WHERE status IN ('PENDING','RUNNING') ORDER BY created_at",
            this::mapRow
        );
    }
}
