package com.eaglepoint.console.repository;

import com.eaglepoint.console.model.BedBuilding;
import com.eaglepoint.console.model.PagedResult;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class BedBuildingRepository extends BaseRepository {

    public BedBuildingRepository(DataSource ds) {
        super(ds);
    }

    private BedBuilding mapRow(ResultSet rs) throws SQLException {
        BedBuilding b = new BedBuilding();
        b.setId(rs.getLong("id"));
        b.setServiceAreaId(getNullableLong(rs, "service_area_id"));
        b.setName(rs.getString("name"));
        b.setAddress(rs.getString("address"));
        b.setCreatedAt(rs.getString("created_at"));
        b.setUpdatedAt(rs.getString("updated_at"));
        return b;
    }

    public long insert(BedBuilding building) {
        return insertAndGetId(
            "INSERT INTO bed_buildings (service_area_id, name, address) VALUES (?,?,?)",
            building.getServiceAreaId(), building.getName(), building.getAddress()
        );
    }

    public Optional<BedBuilding> findById(long id) {
        return queryOne("SELECT * FROM bed_buildings WHERE id=?", this::mapRow, id);
    }

    public PagedResult<BedBuilding> findAll(int page, int pageSize) {
        return paginate("SELECT * FROM bed_buildings ORDER BY name",
            "SELECT COUNT(*) FROM bed_buildings", this::mapRow, page, pageSize);
    }

    public void update(BedBuilding building) {
        execute(
            "UPDATE bed_buildings SET name=?, address=?, updated_at=datetime('now') WHERE id=?",
            building.getName(), building.getAddress(), building.getId()
        );
    }

    public void delete(long id) {
        execute("DELETE FROM bed_buildings WHERE id=?", id);
    }
}
