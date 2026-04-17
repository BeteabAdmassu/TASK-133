package com.eaglepoint.console.repository;

import com.eaglepoint.console.model.UpdateHistoryEntry;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class UpdateHistoryRepository extends BaseRepository {

    public UpdateHistoryRepository(DataSource ds) {
        super(ds);
    }

    private UpdateHistoryEntry mapRow(ResultSet rs) throws SQLException {
        UpdateHistoryEntry e = new UpdateHistoryEntry();
        e.setId(rs.getLong("id"));
        e.setPackageName(rs.getString("package_name"));
        e.setFromVersion(rs.getString("from_version"));
        e.setToVersion(rs.getString("to_version"));
        e.setAction(rs.getString("action"));
        e.setStatus(rs.getString("status"));
        e.setSha256Hash(rs.getString("sha256_hash"));
        e.setSignatureStatus(rs.getString("signature_status"));
        e.setInstalledPath(rs.getString("installed_path"));
        e.setBackupPath(rs.getString("backup_path"));
        e.setErrorMessage(rs.getString("error_message"));
        e.setInitiatedBy(getNullableLong(rs, "initiated_by"));
        e.setOccurredAt(rs.getString("occurred_at"));
        e.setNotes(rs.getString("notes"));
        return e;
    }

    public long insert(UpdateHistoryEntry e) {
        return insertAndGetId(
            "INSERT INTO update_history (package_name, from_version, to_version, action, status, " +
            "sha256_hash, signature_status, installed_path, backup_path, error_message, initiated_by, notes) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
            e.getPackageName(), e.getFromVersion(), e.getToVersion(),
            e.getAction(), e.getStatus(),
            e.getSha256Hash(), e.getSignatureStatus(),
            e.getInstalledPath(), e.getBackupPath(), e.getErrorMessage(),
            e.getInitiatedBy(), e.getNotes()
        );
    }

    public List<UpdateHistoryEntry> findAll(int limit) {
        return queryList(
            "SELECT * FROM update_history ORDER BY id DESC LIMIT ?",
            this::mapRow, limit
        );
    }

    /** Most recent successful INSTALLED entry — the row considered "current". */
    public Optional<UpdateHistoryEntry> findCurrentInstalled() {
        return queryOne(
            "SELECT * FROM update_history " +
            "WHERE action = 'INSTALLED' AND status = 'SUCCESS' " +
            "ORDER BY id DESC LIMIT 1",
            this::mapRow
        );
    }

    /**
     * Most recent SUCCESS INSTALLED row before the given id — used as the
     * rollback target.
     */
    public Optional<UpdateHistoryEntry> findPreviousInstalledBefore(long id) {
        return queryOne(
            "SELECT * FROM update_history " +
            "WHERE id < ? AND action = 'INSTALLED' AND status = 'SUCCESS' " +
            "ORDER BY id DESC LIMIT 1",
            this::mapRow, id
        );
    }
}
