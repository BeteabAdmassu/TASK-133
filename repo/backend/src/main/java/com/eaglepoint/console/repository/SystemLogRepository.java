package com.eaglepoint.console.repository;

import com.eaglepoint.console.model.PagedResult;
import com.eaglepoint.console.model.SystemLog;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class SystemLogRepository extends BaseRepository {

    public SystemLogRepository(DataSource ds) {
        super(ds);
    }

    private SystemLog mapRow(ResultSet rs) throws SQLException {
        SystemLog l = new SystemLog();
        l.setId(rs.getLong("id"));
        l.setLevel(rs.getString("level"));
        l.setCategory(rs.getString("category"));
        l.setMessage(rs.getString("message"));
        l.setEntityType(rs.getString("entity_type"));
        l.setEntityId(getNullableLong(rs, "entity_id"));
        l.setUserId(getNullableLong(rs, "user_id"));
        l.setTraceId(rs.getString("trace_id"));
        l.setRequestId(rs.getString("request_id"));
        l.setPath(rs.getString("path"));
        l.setStatusCode(getNullableInt(rs, "status_code"));
        l.setDurationMs(getNullableInt(rs, "duration_ms"));
        l.setCreatedAt(rs.getString("created_at"));
        return l;
    }

    public long insert(SystemLog log) {
        return insertAndGetId(
            "INSERT INTO system_logs (level, category, message, entity_type, entity_id, user_id, trace_id, request_id, path, status_code, duration_ms) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
            log.getLevel(), log.getCategory(), log.getMessage(),
            log.getEntityType(), log.getEntityId(), log.getUserId(),
            log.getTraceId(), log.getRequestId(), log.getPath(),
            log.getStatusCode(), log.getDurationMs()
        );
    }

    public PagedResult<SystemLog> findAll(String level, String category, String from, String to,
                                          int page, int pageSize) {
        StringBuilder sql = new StringBuilder("SELECT * FROM system_logs WHERE 1=1");
        StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM system_logs WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (level != null) { sql.append(" AND level=?"); countSql.append(" AND level=?"); params.add(level); }
        if (category != null) { sql.append(" AND category=?"); countSql.append(" AND category=?"); params.add(category); }
        if (from != null) { sql.append(" AND created_at>=?"); countSql.append(" AND created_at>=?"); params.add(from); }
        if (to != null) { sql.append(" AND created_at<=?"); countSql.append(" AND created_at<=?"); params.add(to); }
        sql.append(" ORDER BY created_at DESC");

        return paginate(sql.toString(), countSql.toString(), this::mapRow, page, pageSize, params.toArray());
    }
}
