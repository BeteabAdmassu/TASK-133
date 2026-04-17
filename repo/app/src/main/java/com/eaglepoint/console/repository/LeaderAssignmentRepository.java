package com.eaglepoint.console.repository;

import com.eaglepoint.console.model.LeaderAssignment;
import com.eaglepoint.console.model.PagedResult;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class LeaderAssignmentRepository extends BaseRepository {

    public LeaderAssignmentRepository(DataSource ds) {
        super(ds);
    }

    private LeaderAssignment mapRow(ResultSet rs) throws SQLException {
        LeaderAssignment la = new LeaderAssignment();
        la.setId(rs.getLong("id"));
        la.setServiceAreaId(rs.getLong("service_area_id"));
        la.setUserId(rs.getLong("user_id"));
        la.setAssignedBy(rs.getLong("assigned_by"));
        la.setAssignedAt(rs.getString("assigned_at"));
        la.setUnassignedAt(rs.getString("unassigned_at"));
        la.setCreatedAt(rs.getString("created_at"));
        la.setUpdatedAt(rs.getString("updated_at"));
        return la;
    }

    public Optional<LeaderAssignment> findById(long id) {
        return queryOne("SELECT * FROM leader_assignments WHERE id = ?", this::mapRow, id);
    }

    public PagedResult<LeaderAssignment> findByServiceArea(long serviceAreaId, int page, int pageSize) {
        return paginate("SELECT * FROM leader_assignments WHERE service_area_id = ? ORDER BY assigned_at DESC",
            "SELECT COUNT(*) FROM leader_assignments WHERE service_area_id = ?",
            this::mapRow, page, pageSize, serviceAreaId);
    }

    public PagedResult<LeaderAssignment> findAll(int page, int pageSize) {
        return paginate("SELECT * FROM leader_assignments ORDER BY assigned_at DESC",
            "SELECT COUNT(*) FROM leader_assignments", this::mapRow, page, pageSize);
    }

    public List<LeaderAssignment> findActiveByArea(long serviceAreaId) {
        return queryList(
            "SELECT * FROM leader_assignments WHERE service_area_id = ? AND unassigned_at IS NULL",
            this::mapRow, serviceAreaId
        );
    }

    public long insert(LeaderAssignment la) {
        return insertAndGetId(
            "INSERT INTO leader_assignments (service_area_id, user_id, assigned_by, assigned_at) VALUES (?,?,?,datetime('now'))",
            la.getServiceAreaId(), la.getUserId(), la.getAssignedBy()
        );
    }

    public void endAssignment(long id, String unassignedAt) {
        execute(
            "UPDATE leader_assignments SET unassigned_at=?, updated_at=datetime('now') WHERE id=?",
            unassignedAt, id
        );
    }
}
