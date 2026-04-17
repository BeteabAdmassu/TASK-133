package com.eaglepoint.console.repository;

import com.eaglepoint.console.model.PagedResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public abstract class BaseRepository {
    private static final Logger log = LoggerFactory.getLogger(BaseRepository.class);
    protected final DataSource ds;

    protected BaseRepository(DataSource ds) {
        this.ds = ds;
    }

    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    protected <T> List<T> queryList(String sql, RowMapper<T> mapper, Object... params) {
        List<T> results = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = prepareStatement(conn, sql, params);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(mapper.map(rs));
            }
        } catch (SQLException e) {
            log.error("Query error: {}", e.getMessage(), e);
            throw new RuntimeException("Database query failed", e);
        }
        return results;
    }

    protected <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params) {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = prepareStatement(conn, sql, params);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return Optional.of(mapper.map(rs));
        } catch (SQLException e) {
            log.error("Query error: {}", e.getMessage(), e);
            throw new RuntimeException("Database query failed", e);
        }
        return Optional.empty();
    }

    protected <T> PagedResult<T> paginate(String sql, String countSql, RowMapper<T> mapper,
                                          int page, int pageSize, Object... params) {
        int offset = (page - 1) * pageSize;
        String pagedSql = sql + " LIMIT ? OFFSET ?";

        int total = 0;
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = prepareStatement(conn, countSql, params);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) total = rs.getInt(1);
        } catch (SQLException e) {
            log.error("Count query error: {}", e.getMessage(), e);
            throw new RuntimeException("Database count query failed", e);
        }

        Object[] pagedParams = appendParams(params, offset, pageSize);
        // Note: LIMIT/OFFSET requires pageSize before offset in typical SQL; but we use LIMIT pageSize OFFSET offset
        Object[] correctParams = appendParams(params, pageSize, offset);

        List<T> data = queryList(pagedSql, mapper, correctParams);
        return new PagedResult<>(data, total, page, pageSize);
    }

    protected void execute(String sql, Object... params) {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = prepareStatement(conn, sql, params)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Execute error: {}", e.getMessage(), e);
            throw new RuntimeException("Database execute failed: " + e.getMessage(), e);
        }
    }

    protected long insertAndGetId(String sql, Object... params) {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            setParams(ps, params);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
            throw new RuntimeException("No generated key returned");
        } catch (SQLException e) {
            log.error("Insert error: {}", e.getMessage(), e);
            throw new RuntimeException("Database insert failed: " + e.getMessage(), e);
        }
    }

    private PreparedStatement prepareStatement(Connection conn, String sql, Object... params) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(sql);
        setParams(ps, params);
        return ps;
    }

    private void setParams(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            if (params[i] == null) {
                ps.setNull(i + 1, Types.NULL);
            } else {
                ps.setObject(i + 1, params[i]);
            }
        }
    }

    private Object[] appendParams(Object[] original, Object... extra) {
        Object[] result = new Object[original.length + extra.length];
        System.arraycopy(original, 0, result, 0, original.length);
        System.arraycopy(extra, 0, result, original.length, extra.length);
        return result;
    }

    protected Long getNullableLong(ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }

    protected Integer getNullableInt(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    protected Double getNullableDouble(ResultSet rs, String col) throws SQLException {
        double v = rs.getDouble(col);
        return rs.wasNull() ? null : v;
    }
}
