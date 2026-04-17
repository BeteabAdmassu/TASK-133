package com.eaglepoint.console.repository;

import com.eaglepoint.console.model.AuditTrail;
import com.eaglepoint.console.model.PagedResult;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class AuditTrailRepository extends BaseRepository {

    public AuditTrailRepository(DataSource ds) {
        super(ds);
    }

    private AuditTrail mapRow(ResultSet rs) throws SQLException {
        AuditTrail a = new AuditTrail();
        a.setId(rs.getLong("id"));
        a.setEntityType(rs.getString("entity_type"));
        a.setEntityId(rs.getLong("entity_id"));
        a.setAction(rs.getString("action"));
        a.setUserId(rs.getLong("user_id"));
        a.setTraceId(rs.getString("trace_id"));
        a.setOccurredAt(rs.getString("occurred_at"));
        a.setOldValuesJson(rs.getString("old_values_json"));
        a.setNewValuesJson(rs.getString("new_values_json"));
        a.setNotes(rs.getString("notes"));
        return a;
    }

    public long insert(AuditTrail a) {
        return insertAndGetId(
            "INSERT INTO audit_trail (entity_type, entity_id, action, user_id, trace_id, occurred_at, old_values_json, new_values_json, notes) VALUES (?,?,?,?,?,datetime('now'),?,?,?)",
            a.getEntityType(), a.getEntityId(), a.getAction(), a.getUserId(), a.getTraceId(),
            a.getOldValuesJson(), a.getNewValuesJson(), a.getNotes()
        );
    }

    public PagedResult<AuditTrail> findAll(String entityType, Long entityId, Long userId,
                                           String from, String to, int page, int pageSize) {
        StringBuilder sql = new StringBuilder("SELECT * FROM audit_trail WHERE 1=1");
        StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM audit_trail WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (entityType != null) { sql.append(" AND entity_type=?"); countSql.append(" AND entity_type=?"); params.add(entityType); }
        if (entityId != null) { sql.append(" AND entity_id=?"); countSql.append(" AND entity_id=?"); params.add(entityId); }
        if (userId != null) { sql.append(" AND user_id=?"); countSql.append(" AND user_id=?"); params.add(userId); }
        if (from != null) { sql.append(" AND occurred_at>=?"); countSql.append(" AND occurred_at>=?"); params.add(from); }
        if (to != null) { sql.append(" AND occurred_at<=?"); countSql.append(" AND occurred_at<=?"); params.add(to); }
        sql.append(" ORDER BY occurred_at DESC");

        return paginate(sql.toString(), countSql.toString(), this::mapRow, page, pageSize, params.toArray());
    }
}
