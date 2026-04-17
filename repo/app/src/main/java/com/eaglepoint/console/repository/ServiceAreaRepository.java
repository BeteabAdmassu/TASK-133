package com.eaglepoint.console.repository;

import com.eaglepoint.console.model.PagedResult;
import com.eaglepoint.console.model.ServiceArea;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class ServiceAreaRepository extends BaseRepository {

    public ServiceAreaRepository(DataSource ds) {
        super(ds);
    }

    private ServiceArea mapRow(ResultSet rs) throws SQLException {
        ServiceArea sa = new ServiceArea();
        sa.setId(rs.getLong("id"));
        sa.setCommunityId(rs.getLong("community_id"));
        sa.setName(rs.getString("name"));
        sa.setDescription(rs.getString("description"));
        sa.setStatus(rs.getString("status"));
        sa.setCreatedAt(rs.getString("created_at"));
        sa.setUpdatedAt(rs.getString("updated_at"));
        return sa;
    }

    public Optional<ServiceArea> findById(long id) {
        return queryOne("SELECT * FROM service_areas WHERE id = ?", this::mapRow, id);
    }

    public PagedResult<ServiceArea> findByCommunity(long communityId, int page, int pageSize) {
        return paginate("SELECT * FROM service_areas WHERE community_id = ? ORDER BY name",
            "SELECT COUNT(*) FROM service_areas WHERE community_id = ?",
            this::mapRow, page, pageSize, communityId);
    }

    public PagedResult<ServiceArea> findAll(int page, int pageSize) {
        return paginate("SELECT * FROM service_areas ORDER BY name",
            "SELECT COUNT(*) FROM service_areas", this::mapRow, page, pageSize);
    }

    public Optional<ServiceArea> findByNameAndCommunity(String name, long communityId) {
        return queryOne("SELECT * FROM service_areas WHERE name = ? AND community_id = ?",
            this::mapRow, name, communityId);
    }

    public long insert(ServiceArea sa) {
        return insertAndGetId(
            "INSERT INTO service_areas (community_id, name, description, status) VALUES (?,?,?,?)",
            sa.getCommunityId(), sa.getName(), sa.getDescription(),
            sa.getStatus() != null ? sa.getStatus() : "ACTIVE"
        );
    }

    public void update(ServiceArea sa) {
        execute(
            "UPDATE service_areas SET name=?, description=?, status=?, updated_at=datetime('now') WHERE id=?",
            sa.getName(), sa.getDescription(), sa.getStatus(), sa.getId()
        );
    }

    public void delete(long id) {
        execute("DELETE FROM service_areas WHERE id = ?", id);
    }
}
