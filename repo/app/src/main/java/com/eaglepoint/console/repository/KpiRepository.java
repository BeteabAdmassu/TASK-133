package com.eaglepoint.console.repository;

import com.eaglepoint.console.model.KpiDefinition;
import com.eaglepoint.console.model.KpiScore;
import com.eaglepoint.console.model.PagedResult;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class KpiRepository extends BaseRepository {

    public KpiRepository(DataSource ds) {
        super(ds);
    }

    private KpiDefinition mapKpi(ResultSet rs) throws SQLException {
        KpiDefinition k = new KpiDefinition();
        k.setId(rs.getLong("id"));
        k.setName(rs.getString("name"));
        k.setDescription(rs.getString("description"));
        k.setUnit(rs.getString("unit"));
        k.setCategory(rs.getString("category"));
        k.setFormula(rs.getString("formula"));
        k.setActive(rs.getInt("is_active") == 1);
        k.setCreatedAt(rs.getString("created_at"));
        k.setUpdatedAt(rs.getString("updated_at"));
        return k;
    }

    private KpiScore mapScore(ResultSet rs) throws SQLException {
        KpiScore s = new KpiScore();
        s.setId(rs.getLong("id"));
        s.setKpiId(rs.getLong("kpi_id"));
        s.setServiceAreaId(getNullableLong(rs, "service_area_id"));
        s.setCycleId(getNullableLong(rs, "cycle_id"));
        s.setScoreDate(rs.getString("score_date"));
        s.setValue(rs.getDouble("value"));
        s.setComputedBy(getNullableLong(rs, "computed_by"));
        s.setNotes(rs.getString("notes"));
        s.setCreatedAt(rs.getString("created_at"));
        return s;
    }

    public Optional<KpiDefinition> findKpiById(long id) {
        return queryOne("SELECT * FROM kpi_definitions WHERE id = ?", this::mapKpi, id);
    }

    public Optional<KpiDefinition> findKpiByName(String name) {
        return queryOne("SELECT * FROM kpi_definitions WHERE name = ?", this::mapKpi, name);
    }

    public PagedResult<KpiDefinition> findAllKpis(int page, int pageSize) {
        return paginate("SELECT * FROM kpi_definitions ORDER BY name",
            "SELECT COUNT(*) FROM kpi_definitions", this::mapKpi, page, pageSize);
    }

    public long insertKpi(KpiDefinition k) {
        return insertAndGetId(
            "INSERT INTO kpi_definitions (name, description, unit, category, formula, is_active) VALUES (?,?,?,?,?,1)",
            k.getName(), k.getDescription(), k.getUnit(), k.getCategory(), k.getFormula()
        );
    }

    public void updateKpi(KpiDefinition k) {
        execute(
            "UPDATE kpi_definitions SET name=?, description=?, unit=?, category=?, formula=?, is_active=?, updated_at=datetime('now') WHERE id=?",
            k.getName(), k.getDescription(), k.getUnit(), k.getCategory(),
            k.getFormula(), k.isActive() ? 1 : 0, k.getId()
        );
    }

    public PagedResult<KpiScore> findScores(Long kpiId, Long serviceAreaId, String from, String to, int page, int pageSize) {
        StringBuilder sql = new StringBuilder("SELECT * FROM kpi_scores WHERE 1=1");
        StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM kpi_scores WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (kpiId != null) { sql.append(" AND kpi_id=?"); countSql.append(" AND kpi_id=?"); params.add(kpiId); }
        if (serviceAreaId != null) { sql.append(" AND service_area_id=?"); countSql.append(" AND service_area_id=?"); params.add(serviceAreaId); }
        if (from != null) { sql.append(" AND score_date>=?"); countSql.append(" AND score_date>=?"); params.add(from); }
        if (to != null) { sql.append(" AND score_date<=?"); countSql.append(" AND score_date<=?"); params.add(to); }
        sql.append(" ORDER BY score_date DESC");

        return paginate(sql.toString(), countSql.toString(), this::mapScore, page, pageSize, params.toArray());
    }

    public long insertScore(KpiScore s) {
        return insertAndGetId(
            "INSERT INTO kpi_scores (kpi_id, service_area_id, cycle_id, score_date, value, computed_by, notes) VALUES (?,?,?,?,?,?,?)",
            s.getKpiId(), s.getServiceAreaId(), s.getCycleId(), s.getScoreDate(),
            s.getValue(), s.getComputedBy(), s.getNotes()
        );
    }
}
