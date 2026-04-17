package com.eaglepoint.console.repository;

import com.eaglepoint.console.model.Community;
import com.eaglepoint.console.model.PagedResult;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class CommunityRepository extends BaseRepository {

    public CommunityRepository(DataSource ds) {
        super(ds);
    }

    private Community mapRow(ResultSet rs) throws SQLException {
        Community c = new Community();
        c.setId(rs.getLong("id"));
        c.setName(rs.getString("name"));
        c.setDescription(rs.getString("description"));
        c.setStatus(rs.getString("status"));
        c.setCreatedAt(rs.getString("created_at"));
        c.setUpdatedAt(rs.getString("updated_at"));
        return c;
    }

    public Optional<Community> findById(long id) {
        return queryOne("SELECT * FROM communities WHERE id = ?", this::mapRow, id);
    }

    public Optional<Community> findByName(String name) {
        return queryOne("SELECT * FROM communities WHERE name = ?", this::mapRow, name);
    }

    public PagedResult<Community> findAll(int page, int pageSize) {
        return paginate("SELECT * FROM communities ORDER BY name",
            "SELECT COUNT(*) FROM communities", this::mapRow, page, pageSize);
    }

    public long insert(Community c) {
        return insertAndGetId(
            "INSERT INTO communities (name, description, status) VALUES (?,?,?)",
            c.getName(), c.getDescription(), c.getStatus() != null ? c.getStatus() : "ACTIVE"
        );
    }

    public void update(Community c) {
        execute(
            "UPDATE communities SET name=?, description=?, status=?, updated_at=datetime('now') WHERE id=?",
            c.getName(), c.getDescription(), c.getStatus(), c.getId()
        );
    }

    public void delete(long id) {
        execute("DELETE FROM communities WHERE id = ?", id);
    }

    public boolean hasServiceAreas(long id) {
        return queryOne("SELECT COUNT(*) as cnt FROM service_areas WHERE community_id = ?",
            rs -> rs.getInt("cnt") > 0, id).orElse(false);
    }
}
