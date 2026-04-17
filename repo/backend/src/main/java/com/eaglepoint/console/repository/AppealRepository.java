package com.eaglepoint.console.repository;

import com.eaglepoint.console.model.Appeal;
import com.eaglepoint.console.model.PagedResult;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class AppealRepository extends BaseRepository {

    public AppealRepository(DataSource ds) {
        super(ds);
    }

    private Appeal mapRow(ResultSet rs) throws SQLException {
        Appeal a = new Appeal();
        a.setId(rs.getLong("id"));
        a.setScorecardId(rs.getLong("scorecard_id"));
        a.setFiledBy(rs.getLong("filed_by"));
        a.setFiledAt(rs.getString("filed_at"));
        a.setDeadline(rs.getString("deadline"));
        a.setReason(rs.getString("reason"));
        a.setStatus(rs.getString("status"));
        a.setResolutionNotes(rs.getString("resolution_notes"));
        a.setResolvedAt(rs.getString("resolved_at"));
        a.setCreatedAt(rs.getString("created_at"));
        a.setUpdatedAt(rs.getString("updated_at"));
        return a;
    }

    public long insert(long scorecardId, String reason, long filedBy) {
        return insertAndGetId(
            "INSERT INTO appeals (scorecard_id, filed_by, reason, status, deadline) VALUES (?,?,?,'PENDING',datetime('now','+7 days'))",
            scorecardId, filedBy, reason
        );
    }

    public Optional<Appeal> findById(long id) {
        return queryOne("SELECT * FROM appeals WHERE id=?", this::mapRow, id);
    }

    public boolean existsByScorecard(long scorecardId) {
        return queryOne("SELECT id FROM appeals WHERE scorecard_id=? AND status IN ('PENDING','UNDER_REVIEW')",
            rs -> rs.getLong("id"), scorecardId).isPresent();
    }

    public PagedResult<Appeal> findAll(int page, int pageSize) {
        return paginate("SELECT * FROM appeals ORDER BY filed_at DESC",
            "SELECT COUNT(*) FROM appeals", this::mapRow, page, pageSize);
    }

    public void update(Appeal appeal) {
        execute(
            "UPDATE appeals SET status=?, resolution_notes=?, resolved_at=?, updated_at=datetime('now') WHERE id=?",
            appeal.getStatus(), appeal.getResolutionNotes(),
            appeal.getResolvedAt(), appeal.getId()
        );
    }
}
