package com.eaglepoint.console.repository;

import com.eaglepoint.console.model.PagedResult;
import com.eaglepoint.console.model.RouteCheckpoint;
import com.eaglepoint.console.model.RouteImport;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class RouteImportRepository extends BaseRepository {

    public RouteImportRepository(DataSource ds) {
        super(ds);
    }

    private RouteImport mapImport(ResultSet rs) throws SQLException {
        RouteImport ri = new RouteImport();
        ri.setId(rs.getLong("id"));
        ri.setFilename(rs.getString("filename"));
        ri.setImportedBy(rs.getLong("imported_by"));
        ri.setImportedAt(rs.getString("imported_at"));
        ri.setStatus(rs.getString("status"));
        ri.setRecordCount(getNullableInt(rs, "record_count"));
        ri.setErrorCount(getNullableInt(rs, "error_count"));
        ri.setCheckpointPath(rs.getString("checkpoint_path"));
        ri.setCreatedAt(rs.getString("created_at"));
        ri.setUpdatedAt(rs.getString("updated_at"));
        return ri;
    }

    private RouteCheckpoint mapCheckpoint(ResultSet rs) throws SQLException {
        RouteCheckpoint c = new RouteCheckpoint();
        c.setId(rs.getLong("id"));
        c.setImportId(rs.getLong("import_id"));
        c.setCheckpointName(rs.getString("checkpoint_name"));
        c.setExpectedAt(rs.getString("expected_at"));
        c.setActualAt(rs.getString("actual_at"));
        c.setLatMasked(getNullableDouble(rs, "lat_masked"));
        c.setLonMasked(getNullableDouble(rs, "lon_masked"));
        c.setDeviationMiles(getNullableDouble(rs, "deviation_miles"));
        c.setDeviationAlert(rs.getInt("is_deviation_alert") == 1);
        c.setMissedAlert(rs.getInt("is_missed_alert") == 1);
        c.setStatus(rs.getString("status"));
        c.setCreatedAt(rs.getString("created_at"));
        return c;
    }

    public Optional<RouteImport> findById(long id) {
        return queryOne("SELECT * FROM route_imports WHERE id = ?", this::mapImport, id);
    }

    public PagedResult<RouteImport> findAll(int page, int pageSize) {
        return paginate("SELECT * FROM route_imports ORDER BY imported_at DESC",
            "SELECT COUNT(*) FROM route_imports", this::mapImport, page, pageSize);
    }

    public long insert(RouteImport ri) {
        return insertAndGetId(
            "INSERT INTO route_imports (filename, imported_by, status) VALUES (?,?,'PENDING')",
            ri.getFilename(), ri.getImportedBy()
        );
    }

    public void updateStatus(long id, String status, Integer recordCount, Integer errorCount) {
        execute(
            "UPDATE route_imports SET status=?, record_count=?, error_count=?, updated_at=datetime('now') WHERE id=?",
            status, recordCount, errorCount, id
        );
    }

    public void updateCheckpointPath(long id, String path) {
        execute(
            "UPDATE route_imports SET checkpoint_path=?, updated_at=datetime('now') WHERE id=?",
            path, id
        );
    }

    public long insertCheckpoint(RouteCheckpoint c) {
        return insertAndGetId(
            "INSERT INTO route_checkpoints (import_id, checkpoint_name, expected_at, actual_at, lat_masked, lon_masked, deviation_miles, is_deviation_alert, is_missed_alert, status) VALUES (?,?,?,?,?,?,?,?,?,?)",
            c.getImportId(), c.getCheckpointName(), c.getExpectedAt(), c.getActualAt(),
            c.getLatMasked(), c.getLonMasked(), c.getDeviationMiles(),
            c.isDeviationAlert() ? 1 : 0, c.isMissedAlert() ? 1 : 0, c.getStatus()
        );
    }

    public PagedResult<RouteCheckpoint> findCheckpointsByImport(long importId, int page, int pageSize) {
        return paginate("SELECT * FROM route_checkpoints WHERE import_id = ? ORDER BY id",
            "SELECT COUNT(*) FROM route_checkpoints WHERE import_id = ?",
            this::mapCheckpoint, page, pageSize, importId);
    }

    /** Returns imports in any non-terminal status — used by crash-safe resume. */
    public java.util.List<RouteImport> findIncomplete() {
        return queryList(
            "SELECT * FROM route_imports WHERE status IN ('PENDING','VALIDATING','PROCESSING') ORDER BY id",
            this::mapImport
        );
    }

    /** Count already-committed checkpoints for an import — resume pointer. */
    public int countCheckpoints(long importId) {
        return queryOne(
            "SELECT COUNT(*) as cnt FROM route_checkpoints WHERE import_id = ?",
            rs -> rs.getInt("cnt"), importId
        ).orElse(0);
    }

    /** Count checkpoints flagged with a deviation or missed alert for an import. */
    public int countAlertCheckpoints(long importId) {
        return queryOne(
            "SELECT COUNT(*) as cnt FROM route_checkpoints WHERE import_id = ? AND (is_deviation_alert = 1 OR is_missed_alert = 1)",
            rs -> rs.getInt("cnt"), importId
        ).orElse(0);
    }
}
