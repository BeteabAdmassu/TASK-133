package com.eaglepoint.console.repository;

import com.eaglepoint.console.model.Geozone;
import com.eaglepoint.console.model.PagedResult;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class GeozoneRepository extends BaseRepository {

    public GeozoneRepository(DataSource ds) {
        super(ds);
    }

    private Geozone mapRow(ResultSet rs) throws SQLException {
        Geozone g = new Geozone();
        g.setId(rs.getLong("id"));
        g.setName(rs.getString("name"));
        g.setZipCodes(rs.getString("zip_codes"));
        g.setStreetRanges(rs.getString("street_ranges"));
        g.setCreatedAt(rs.getString("created_at"));
        g.setUpdatedAt(rs.getString("updated_at"));
        return g;
    }

    public Optional<Geozone> findById(long id) {
        return queryOne("SELECT * FROM geozones WHERE id = ?", this::mapRow, id);
    }

    public PagedResult<Geozone> findAll(int page, int pageSize) {
        return paginate("SELECT * FROM geozones ORDER BY name",
            "SELECT COUNT(*) FROM geozones", this::mapRow, page, pageSize);
    }

    public List<Geozone> findByZipCode(String zipCode) {
        return queryList(
            "SELECT * FROM geozones WHERE zip_codes LIKE ?", this::mapRow,
            "%" + zipCode + "%"
        );
    }

    public long insert(Geozone g) {
        return insertAndGetId(
            "INSERT INTO geozones (name, zip_codes, street_ranges) VALUES (?,?,?)",
            g.getName(), g.getZipCodes(), g.getStreetRanges()
        );
    }

    public void update(Geozone g) {
        execute(
            "UPDATE geozones SET name=?, zip_codes=?, street_ranges=?, updated_at=datetime('now') WHERE id=?",
            g.getName(), g.getZipCodes(), g.getStreetRanges(), g.getId()
        );
    }

    public void delete(long id) {
        execute("DELETE FROM geozones WHERE id = ?", id);
    }
}
